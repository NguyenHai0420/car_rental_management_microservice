param(
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs', 'init', 'down')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
$composeArguments = @('compose', '--project-name', 'car-rental-minio', '--project-directory', $PSScriptRoot, '--file', $composeFile)
$localEnv = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $localEnv) {
    $composeArguments += @('--env-file', $localEnv)
}

function Invoke-MinioDocker {
    param([string[]]$DockerArguments)
    & docker @DockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code $LASTEXITCODE."
    }
}

function Ensure-MinioResource {
    param([ValidateSet('volume', 'network')][string]$ResourceType, [string]$ResourceName)
    $existingNames = & docker $ResourceType ls --format '{{.Name}}'
    if ($LASTEXITCODE -ne 0) {
        throw 'Cannot connect to Docker Engine. Start Docker Desktop and try again.'
    }
    if ($existingNames -notcontains $ResourceName) {
        Invoke-MinioDocker -DockerArguments @($ResourceType, 'create', $ResourceName)
    }
}

function Start-MinioService {
    # Resolve the same .env/interpolation as Compose without displaying credentials.
    $rawConfig = & docker @composeArguments config --format json
    if ($LASTEXITCODE -ne 0) {
        throw 'Invalid MinIO Compose configuration.'
    }
    $config = $rawConfig | ConvertFrom-Json
    Ensure-MinioResource -ResourceType volume -ResourceName $config.volumes.'minio-data'.name
    Ensure-MinioResource -ResourceType network -ResourceName $config.networks.storage.name
    Invoke-MinioDocker -DockerArguments ($composeArguments + @('build', 'minio-api'))
    Invoke-MinioDocker -DockerArguments ($composeArguments + @('up', '-d', '--wait', '--wait-timeout', '60', 'minio'))
    Invoke-MinioDocker -DockerArguments ($composeArguments + @('run', '--rm', '--no-deps', 'minio-init'))
    Invoke-MinioDocker -DockerArguments ($composeArguments + @('up', '-d', '--no-deps', '--wait', '--wait-timeout', '120', 'minio-api'))
}

switch ($Action) {
    'start' { Start-MinioService }
    'init' { Start-MinioService }
    'restart' {
        Invoke-MinioDocker -DockerArguments ($composeArguments + @('stop', 'minio-api', 'minio'))
        Start-MinioService
    }
    'stop' { Invoke-MinioDocker -DockerArguments ($composeArguments + @('stop', 'minio-api', 'minio')) }
    'status' { Invoke-MinioDocker -DockerArguments ($composeArguments + @('ps', '--all')) }
    'logs' { Invoke-MinioDocker -DockerArguments ($composeArguments + @('logs', '--tail', '100', 'minio-api', 'minio')) }
    'down' { Invoke-MinioDocker -DockerArguments ($composeArguments + @('down')) }
}
