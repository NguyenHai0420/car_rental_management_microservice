// Availability decides which vehicles can be booked; showroom cards supply extra display details.
export function mergeAvailableVehicles(availableVehicles = [], showroomCars = []) {
  const showroomById = new Map(showroomCars.map((car) => [car.id, car]));
  return availableVehicles.flatMap((vehicle) => {
    const id = vehicle.vehicleId || vehicle.id;
    if (!id) return [];
    const card = showroomById.get(id);
    return [{
      ...card,
      ...vehicle,
      id,
      vehicleId: id,
      dailyRate: vehicle.dailyRate ?? card?.dailyRate,
      imageUrl: vehicle.imageUrl || vehicle.primaryImageUrl || card?.imageUrl || null,
    }];
  });
}
