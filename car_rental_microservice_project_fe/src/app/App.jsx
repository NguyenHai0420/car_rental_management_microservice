import React, { useState, useEffect } from "react";
import { PaymentPage } from "../features/payments/pages/PaymentPage";
import { PaymentCallbackPage } from "../features/payments/pages/PaymentCallbackPage";
import { PricingCalculator } from "../features/payments/components/PricingCalculator";
import "../style/App.css";

function App() {
  const [currentPath, setCurrentPath] = useState(() => {
    const path = window.location.pathname;
    return path === "/" || path === "" ? "/rent" : path;
  });

  const [customOrder, setCustomOrder] = useState(() => {
    const saved = sessionStorage.getItem("current_rental_payment");
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {}
    }
    return null;
  });

  useEffect(() => {
    const handlePopState = () => {
      setCurrentPath(window.location.pathname);
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  const navigateTo = (path) => {
    window.history.pushState({}, "", path);
    setCurrentPath(path);
  };

  const handleProceedToPayment = (order) => {
    setCustomOrder(order);
    sessionStorage.setItem("current_rental_payment", JSON.stringify(order));
    navigateTo("/payment");
  };

  const isRentScreen = currentPath === "/rent" || currentPath === "/pricing" || currentPath === "/";
  const isCallbackScreen = currentPath.includes("/payment/callback");
  const isPaymentScreen = currentPath.startsWith("/payment") && !isCallbackScreen;
import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './providers/AuthProvider'
import { VehicleManagementLayout } from '../features/vehicles/pages/VehicleManagementLayout'
import { VehicleCategoryPage } from '../features/vehicles/pages/VehicleCategoryPage'
import { VehicleModelPage } from '../features/vehicles/pages/VehicleModelPage'
import { VehicleDetailPage } from '../features/vehicles/pages/VehicleDetailPage'
import { VehicleFormPage } from '../features/vehicles/pages/VehicleFormPage'
import { VehicleListPage } from '../features/vehicles/pages/VehicleListPage'
import { HomePage } from '../features/home/HomePage'
import '../style/home.css'
import '../style/admin.css'

export default function App() {
  return <AuthProvider><BrowserRouter>
    <Routes>
      <Route path="/vehicles" element={<VehicleManagementLayout />}>
        <Route index element={<VehicleListPage />} />
        <Route path="categories" element={<VehicleCategoryPage />} />
        <Route path="models" element={<VehicleModelPage />} />
        <Route path="new" element={<VehicleFormPage />} />
        <Route path=":id" element={<VehicleDetailPage />} />
        <Route path=":id/edit" element={<VehicleFormPage />} />
      </Route>
      <Route path="*" element={<HomePage />} />
    </Routes>
  </BrowserRouter></AuthProvider>
  return (
    <div className="app-container" style={{ minHeight: "100vh", backgroundColor: "#f1f5f9" }}>
      {/* Thanh Điều Hướng Header */}
      <header style={{
        background: "#0f172a",
        color: "#fff",
        padding: "16px 28px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxShadow: "0 2px 10px rgba(0,0,0,0.15)",
      }}>
        <div style={{ fontWeight: "800", fontSize: "19px", display: "flex", alignItems: "center", gap: "10px" }}>
          🚗 CAR RENTAL CRM — CỔNG TÀI CHÍNH & THANH TOÁN
        </div>
        <nav style={{ display: "flex", gap: "12px" }}>
          {/* Nút 1: Thuê Xe */}
          <button
            onClick={() => navigateTo("/rent")}
            style={{
              background: isRentScreen ? "#2563eb" : "#1e293b",
              color: "#fff",
              border: "1px solid #334155",
              padding: "8px 18px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: "700",
              fontSize: "14px",
              transition: "all 0.2s",
            }}
          >
            🚗 1. Thuê Xe
          </button>

          {/* Nút 2: Thanh Toán VietQR */}
          <button
            onClick={() => navigateTo("/payment")}
            style={{
              background: isPaymentScreen ? "#16a34a" : "#1e293b",
              color: "#fff",
              border: "1px solid #334155",
              padding: "8px 18px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: "700",
              fontSize: "14px",
              transition: "all 0.2s",
            }}
          >
            💳 2. Thanh Toán VietQR
          </button>

          {/* Nút 3: Kết Quả Giao Dịch */}
          <button
            onClick={() => navigateTo("/payment/callback?code=00&method=PAYOS&depositPaid=5000000")}
            style={{
              background: isCallbackScreen ? "#2563eb" : "#1e293b",
              color: "#fff",
              border: "1px solid #334155",
              padding: "8px 18px",
              borderRadius: "8px",
              cursor: "pointer",
              fontWeight: "700",
              fontSize: "14px",
              transition: "all 0.2s",
            }}
          >
            ✓ 3. Kết Quả Giao Dịch
          </button>
        </nav>
      </header>

      {/* Điều phối các màn hình */}
      <main style={{ padding: "26px 16px" }}>
        {isRentScreen ? (
          <PricingCalculator onProceedToPayment={handleProceedToPayment} />
        ) : isCallbackScreen ? (
          <PaymentCallbackPage />
        ) : (
          <PaymentPage customOrder={customOrder} />
        )}
      </main>
    </div>
  );
}

export default App;
