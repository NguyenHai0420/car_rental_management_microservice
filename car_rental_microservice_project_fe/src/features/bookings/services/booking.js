import dayjs from "dayjs";
import apiClient from "../../../services/axios-client";

export const BOOKING_STATUS_CONFIG = {
  PENDING: {
    label: "Chờ đặt cọc",
    color: "gold",
    step: 0,
  },
  CONFIRMED: {
    label: "Đã xác nhận",
    color: "blue",
    step: 1,
  },
  PICKED_UP: {
    label: "Đang thuê xe",
    color: "processing",
    step: 2,
  },
  RETURNED: {
    label: "Đã trả xe",
    color: "cyan",
    step: 3,
  },
  COMPLETED: {
    label: "Hoàn tất",
    color: "success",
    step: 4,
  },
  CANCELLED: {
    label: "Đã hủy",
    color: "error",
    step: -1,
  },
};

export const formatVnd = (amount, currency = "VND") => {
  const numeric = Number(amount || 0);
  if (currency === "VND") {
    return `${numeric.toLocaleString("vi-VN")} ₫`;
  }
  return `${numeric.toLocaleString("vi-VN")} ${currency}`;
};

export const formatDateTime = (value) => {
  if (!value) return "—";
  return dayjs(value).format("DD/MM/YYYY HH:mm");
};

export const calculateRentalDays = (pickup, dropoff) => {
  if (!pickup || !dropoff) return 1;
  const start = dayjs(pickup);
  const end = dayjs(dropoff);
  const diffHours = end.diff(start, "hour", true);
  if (diffHours <= 0) return 1;
  return Math.max(1, Math.ceil(diffHours / 24));
};

const toIsoLocalDateTime = (val) => {
  if (!val) return undefined;
  return dayjs(val).format("YYYY-MM-DDTHH:mm:ss");
};

export const bookingService = {
  async searchAvailableVehicles({
    pickupDatetime,
    returnDatetime,
    categoryId,
    minSeats,
    location,
  } = {}) {
    const params = {
      pickupDatetime: toIsoLocalDateTime(pickupDatetime),
      returnDatetime: toIsoLocalDateTime(returnDatetime),
      categoryId: categoryId || undefined,
      minSeats: minSeats || undefined,
      location: location?.trim() || undefined,
    };
    const response = await apiClient.get("/api/v1/bookings/available-vehicles", {
      params,
    });
    return response.data?.data || [];
  },

  async createBooking(payload) {
    const body = {
      vehicleId: payload.vehicleId,
      pickupDatetime: toIsoLocalDateTime(payload.pickupDatetime),
      returnDatetime: toIsoLocalDateTime(payload.returnDatetime),
      pickupLocation: payload.pickupLocation?.trim(),
      returnLocation:
        payload.returnLocation?.trim() || payload.pickupLocation?.trim(),
      notes: payload.notes?.trim() || undefined,
    };
    const response = await apiClient.post("/api/v1/bookings", body);
    return response.data?.data;
  },

  async getMyBookings() {
    const response = await apiClient.get("/api/v1/bookings/my-bookings");
    return response.data?.data || [];
  },

  async getAllBookings({ status, page = 0, size = 10 } = {}) {
    const params = {
      status: status && status !== "ALL" ? status : undefined,
      page,
      size,
    };
    const response = await apiClient.get("/api/v1/bookings", { params });
    return response.data?.data;
  },

  async getBookingById(id) {
    const response = await apiClient.get(`/api/v1/bookings/${id}`);
    return response.data?.data;
  },

  async cancelBooking(id, reason) {
    const response = await apiClient.patch(`/api/v1/bookings/${id}/cancel`, {
      reason: reason?.trim() || "Khách hàng chủ động hủy đơn",
    });
    return response.data?.data;
  },

  async pickupVehicle(id, { pickupMileage, notes }) {
    const response = await apiClient.patch(`/api/v1/bookings/${id}/pickup`, {
      pickupMileage: Number(pickupMileage),
      notes: notes?.trim() || undefined,
    });
    return response.data?.data;
  },

  async returnVehicle(id, { returnMileage, actualReturnDatetime, notes }) {
    const response = await apiClient.patch(`/api/v1/bookings/${id}/return`, {
      returnMileage: Number(returnMileage),
      actualReturnDatetime: toIsoLocalDateTime(actualReturnDatetime),
      notes: notes?.trim() || undefined,
    });
    return response.data?.data;
  },
};
