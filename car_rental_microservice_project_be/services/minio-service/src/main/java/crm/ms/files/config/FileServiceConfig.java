package crm.ms.files.config;

import crm.ms.files.service.FileService;
import crm.ms.files.service.StorageRules;
import crm.ms.files.service.storage.ObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileServiceProperties.class)
public class FileServiceConfig {
    @Bean
    FileService fileService(ObjectStorage storage, FileServiceProperties properties) {
        var rules = new StorageRules(properties.maxFileSize().toBytes(), properties.maxPresignExpiry(), properties.publicBuckets());
        return new FileService(storage, rules);
    }
}
