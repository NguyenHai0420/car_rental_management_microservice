import apiClient from "../../../services/axios-client";

export const paymentService = {
  /**
   * Tính toán cước thuê xe và số tiền cọc yêu cầu
   */
  calculatePrice: async (payload) => {
    const response = await apiClient.post("/api/v1/pricing/calculate", payload);
    return response.data?.data;
  },

  /**
   * Tạo hóa đơn thanh toán cho đơn thuê xe
   */
  createPayment: async (payload) => {
    const response = await apiClient.post("/api/v1/payments", payload);
    return response.data?.data;
  },

  /**
   * Tra cứu chi tiết hóa đơn theo paymentId
   */
  getPaymentById: async (paymentId) => {
    const response = await apiClient.get(`/api/v1/payments/${paymentId}`);
    return response.data?.data;
  },

  /**
   * Tra cứu hóa đơn theo bookingId
   */
  getPaymentByBookingId: async (bookingId) => {
    const response = await apiClient.get(`/api/v1/payments/booking/${bookingId}`);
    return response.data?.data;
  },

  /**
   * Khởi tạo phiên thanh toán (VNPAY, PayOS VietQR, Mock)
   */
  processCheckout: async (paymentId, payload) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/checkout`, payload);
    return response.data?.data;
  },

  /**
   * Xác thực kết quả thanh toán sau khi cổng thanh toán redirect về
   */
  verifyPaymentCallback: async (params, gateway = "mock") => {
    const query = new URLSearchParams(params).toString();
    const response = await apiClient.get(`/api/v1/payments/callback/verify?gateway=${gateway}&${query}`);
    return response.data?.data;
  },
};
