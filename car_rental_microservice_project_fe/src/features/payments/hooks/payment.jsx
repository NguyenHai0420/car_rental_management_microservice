import { useState, useCallback } from "react";
import { paymentService } from "../services/payment";

export const usePayment = () => {
  const [payment, setPayment] = useState(null);
  const [checkoutData, setCheckoutData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchPaymentByBooking = useCallback(async (bookingId) => {
    setLoading(true);
    setError(null);
    try {
      const data = await paymentService.getPaymentByBookingId(bookingId);
      setPayment(data);
      return data;
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "Không thể tải thông tin thanh toán";
      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const initiateCheckout = useCallback(async (paymentId, payload) => {
    setLoading(true);
    setError(null);
    try {
      const data = await paymentService.processCheckout(paymentId, payload);
      setCheckoutData(data);
      return data;
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "Khởi tạo thanh toán thất bại";
      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const verifyCallback = useCallback(async (params, gateway) => {
    setLoading(true);
    setError(null);
    try {
      const data = await paymentService.verifyPaymentCallback(params, gateway);
      setPayment(data);
      return data;
    } catch (err) {
      const msg = err.response?.data?.message || err.message || "Xác thực giao dịch thất bại";
      setError(msg);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    payment,
    checkoutData,
    loading,
    error,
    fetchPaymentByBooking,
    initiateCheckout,
    verifyCallback,
  };
};
