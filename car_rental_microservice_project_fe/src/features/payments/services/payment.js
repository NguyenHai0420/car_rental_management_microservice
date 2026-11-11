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
   * Lấy danh sách bảng giá đang hiệu lực từ payment-service
   */
  listActiveTiers: async (signal) => {
    const response = await apiClient.get("/api/v1/pricing/tiers", { signal });
    return response.data?.data || [];
  },

  /**
   * Tạo hóa đơn thanh toán cho đơn thuê xe
   */
  createPayment: async (payload) => {
    const response = await apiClient.post("/api/v1/payments", payload);
    return response.data?.data;
  },

  /**
   * Lấy danh sách lịch sử hóa đơn và giao dịch thanh toán đã lưu trong DB
   */
  getPaymentHistory: async (params = {}, signal) => {
    const query = {};
    if (params.customerId) query.customerId = params.customerId;
    if (params.status && params.status !== "ALL") query.status = params.status;
    const response = await apiClient.get("/api/v1/payments/history", { params: query, signal });
    return response.data?.data || [];
  },

  /**
   * Hủy hóa đơn đang chờ thanh toán trong database
   */
  cancelPayment: async (paymentId) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/cancel`);
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
   * Khởi tạo phiên thanh toán VietQR
   */
  processCheckout: async (paymentId, payload) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/checkout`, payload);
    return response.data?.data;
  },

  /**
   * Xác thực kết quả thanh toán sau khi cổng thanh toán redirect về
   */
  verifyPaymentCallback: async (params, gateway = "payos") => {
    const query = new URLSearchParams(params).toString();
    const response = await apiClient.get(`/api/v1/payments/callback/verify?gateway=${gateway}&${query}`);
    return response.data?.data;
  },

  /**
   * Ghi nhận phụ thu / phạt phát sinh (trả muộn, trầy xước)
   */
  addPenaltyFee: async (paymentId, penaltyData) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/penalties`, penaltyData);
    return response.data?.data;
  },

  /**
   * Hoàn tiền cọc cho khách hàng
   */
  refundDeposit: async (paymentId, refundData) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/refund`, refundData);
    return response.data?.data;
  },

  /**
   * Mô phỏng khách vừa chuyển khoản xong (dùng cho Demo & Test tính năng tự động nhảy trang)
   */
  simulatePaymentSuccess: async (paymentId) => {
    const response = await apiClient.post(`/api/v1/payments/${paymentId}/simulate-success`);
    return response.data?.data;
  },
};
