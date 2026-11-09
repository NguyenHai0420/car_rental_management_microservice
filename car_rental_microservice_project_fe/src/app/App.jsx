import React, { useState, useEffect } from "react";
import { PaymentPage } from "../features/payments/pages/PaymentPage";
import { PaymentCallbackPage } from "../features/payments/pages/PaymentCallbackPage";
import "../style/App.css";

function App() {
  const [currentPath, setCurrentPath] = useState(window.location.pathname);

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

  return (
    <div className="app-container">
      {/* Thanh Điều Hướng Đơn Giản */}
      <header style={{
        background: "#1e293b",
        color: "#fff",
        padding: "14px 24px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
      }}>
        <div style={{ fontWeight: "bold", fontSize: "18px", display: "flex", alignItems: "center", gap: "8px" }}>
          🚗 Car Rental CRM — Payment Portal
        </div>
        <nav style={{ display: "flex", gap: "16px" }}>
          <button
            onClick={() => navigateTo("/payment")}
            style={{
              background: currentPath.startsWith("/payment") && !currentPath.includes("callback") ? "#3b82f6" : "transparent",
              color: "#fff",
              border: "none",
              padding: "6px 14px",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            Thanh Toán
          </button>
          <button
            onClick={() => navigateTo("/payment/callback?code=00&method=MOCK&txnId=DEMO123")}
            style={{
              background: currentPath.includes("callback") ? "#3b82f6" : "transparent",
              color: "#fff",
              border: "none",
              padding: "6px 14px",
              borderRadius: "6px",
              cursor: "pointer",
            }}
          >
            Mẫu Kết Quả Callback
          </button>
        </nav>
      </header>

      {/* Điều phối Trang */}
      <main>
        {currentPath.includes("/payment/callback") ? (
          <PaymentCallbackPage />
        ) : (
          <PaymentPage />
        )}
      </main>
    </div>
  );
}

export default App;
