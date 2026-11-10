import { useCallback, useEffect, useMemo, useState } from "react";
import keycloak, { initKeycloak } from "../../services/keycloak";
import { AuthContext } from "./auth-context";

const BUSINESS_ROLES = new Set(["ADMIN", "VEHICLE_OWNER", "CUSTOMER"]);

const extractProfile = () => {
  const parsed = keycloak.tokenParsed;
  if (!parsed) return { user: null, roles: [] };

  const realmRoles = parsed.realm_access?.roles || [];
  const roles = realmRoles.filter((role) => BUSINESS_ROLES.has(role));

  return {
    user: {
      id: parsed.sub,
      username: parsed.preferred_username || "",
      fullName: parsed.name || parsed.preferred_username || "Người dùng",
      email: parsed.email || "",
    },
    roles,
  };
};

export const AuthProvider = ({ children }) => {
  const [authState, setAuthState] = useState({
    initialized: false,
    authenticated: false,
    authError: null,
    user: null,
    roles: [],
  });

  useEffect(() => {
    let active = true;

    keycloak.onTokenExpired = () => {
      keycloak
        .updateToken(30)
        .then((refreshed) => {
          if (refreshed && active) {
            const { user, roles } = extractProfile();
            setAuthState((prev) => ({ ...prev, user, roles }));
          }
        })
        .catch(() => {
          if (active) {
            setAuthState({
              initialized: true,
              authenticated: false,
              authError: null,
              user: null,
              roles: [],
            });
          }
        });
    };

    initKeycloak()
      .then((authenticated) => {
        if (!active) return;
        const { user, roles } = authenticated
          ? extractProfile()
          : { user: null, roles: [] };
        setAuthState({
          initialized: true,
          authenticated: Boolean(authenticated),
          authError: null,
          user,
          roles,
        });
      })
      .catch(() => {
        if (!active) return;
        setAuthState({
          initialized: true,
          authenticated: false,
          authError:
            "Chưa kết nối được máy chủ xác thực Keycloak (:8180). Bạn vẫn có thể tra cứu xe trống công khai.",
          user: null,
          roles: [],
        });
      });

    return () => {
      active = false;
    };
  }, []);

  const hasRole = useCallback(
    (...allowedRoles) => {
      if (!allowedRoles || allowedRoles.length === 0) return true;
      return allowedRoles.some((role) => authState.roles.includes(role));
    },
    [authState.roles]
  );

  const login = useCallback((redirectUri = window.location.href) => {
    return keycloak.login({ redirectUri });
  }, []);

  const register = useCallback((redirectUri = window.location.href) => {
    return keycloak.register({ redirectUri });
  }, []);

  const logout = useCallback(() => {
    return keycloak.logout({ redirectUri: window.location.origin });
  }, []);

  const contextValue = useMemo(
    () => ({
      ...authState,
      hasRole,
      login,
      register,
      logout,
    }),
    [authState, hasRole, login, register, logout]
  );

  return (
    <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
  );
};
