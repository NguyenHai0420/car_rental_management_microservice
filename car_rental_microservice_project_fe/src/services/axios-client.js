import axios from "axios";
import keycloak from "./keycloak";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_GATEWAY_URL || "http://localhost:8080",
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use(
  async (config) => {
    if (keycloak.authenticated) {
      try {
        await keycloak.updateToken(30);
      } catch {
        keycloak.clearToken();
      }
      if (keycloak.token) {
        config.headers.Authorization = `Bearer ${keycloak.token}`;
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const backendMessage =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      "Không thể kết nối tới hệ thống máy chủ.";
    error.userMessage = backendMessage;
    return Promise.reject(error);
  }
);

export default apiClient;
