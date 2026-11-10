import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8180",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "car-rental-realm",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "car-rental-fe",
});

let initPromise = null;

export const initKeycloak = () => {
  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
    });
  }
  return initPromise;
};

export default keycloak;

