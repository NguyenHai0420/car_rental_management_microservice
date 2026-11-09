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
