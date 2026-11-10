import { useCallback, useState } from "react";
import { bookingService } from "../services/booking";

export const useAvailableVehicles = () => {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const searchVehicles = useCallback(async (filters) => {
    setLoading(true);
    setError(null);
    try {
      const data = await bookingService.searchAvailableVehicles(filters);
      setVehicles(Array.isArray(data) ? data : []);
      return data;
    } catch (err) {
      setError(err.userMessage || "Không thể tải danh sách xe trống.");
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  return { vehicles, loading, error, searchVehicles };
};

export const useBookingOperations = () => {
  const [myBookings, setMyBookings] = useState([]);
  const [myLoading, setMyLoading] = useState(false);

  const [staffPage, setStaffPage] = useState({
    content: [],
    totalElements: 0,
    number: 0,
    size: 10,
  });
  const [staffLoading, setStaffLoading] = useState(false);

  const [selectedBooking, setSelectedBooking] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const loadMyBookings = useCallback(async () => {
    setMyLoading(true);
    try {
      const list = await bookingService.getMyBookings();
      setMyBookings(Array.isArray(list) ? list : []);
    } finally {
      setMyLoading(false);
    }
  }, []);

  const loadStaffBookings = useCallback(
    async ({ status = "ALL", page = 0, size = 10 } = {}) => {
      setStaffLoading(true);
      try {
        const pageData = await bookingService.getAllBookings({
          status,
          page,
          size,
        });
        setStaffPage({
          content: pageData?.content || [],
          totalElements: pageData?.totalElements || 0,
          number: pageData?.number ?? page,
          size: pageData?.size ?? size,
        });
      } finally {
        setStaffLoading(false);
      }
    },
    []
  );

  const loadBookingDetail = useCallback(async (bookingId) => {
    setDetailLoading(true);
    try {
      const detail = await bookingService.getBookingById(bookingId);
      setSelectedBooking(detail);
      return detail;
    } finally {
      setDetailLoading(false);
    }
  }, []);

  return {
    myBookings,
    myLoading,
    loadMyBookings,
    staffPage,
    staffLoading,
    loadStaffBookings,
    selectedBooking,
    setSelectedBooking,
    detailLoading,
    loadBookingDetail,
  };
};
