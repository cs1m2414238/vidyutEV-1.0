import React, { useState } from "react";
import "./VidyutRegister.css";
import { apiRequest, saveAuthSession } from "../services/api";
import type { AuthData } from "../services/api";

type AccountType = "EV_OWNER" | "LANDOWNER" | "COMPANY_ADMIN";

type RegisterForm = {
  name: string;
  phone: string;
  email: string;
  password: string;
  accountType: AccountType;
  companyName: string;
  registrationNumber: string;
};

interface RegisterPageProps {
  onRegistered: (auth: AuthData) => void;
  onLogin: () => void;
}

// Vidyut Logo Vector Component
const VidyutLogo: React.FC = () => (
  <div className="vidyut-logo-wrapper">
    <svg
      width="100"
      height="100"
      viewBox="0 0 200 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="brand-logo-img"
    >
      <defs>
        <linearGradient id="vidyutVGradientReg" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#d9f99d" />
          <stop offset="25%" stopColor="#a3e635" />
          <stop offset="60%" stopColor="#22c55e" />
          <stop offset="100%" stopColor="#15803d" />
        </linearGradient>
        <filter id="vGlowReg" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="7" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>
      <path
        d="M 38,30 L 65,36 L 100,165 L 128,75 L 114,80 L 165,15 L 142,58 L 158,58 L 100,185 Z"
        fill="url(#vidyutVGradientReg)"
        filter="url(#vGlowReg)"
      />
    </svg>
    <span className="vidyut-logo-sub">Powering a Smarter Tomorrow</span>
  </div>
);

export default function VidyutRegisterPage({ onRegistered, onLogin }: RegisterPageProps) {
  const [formData, setFormData] = useState<RegisterForm>({
    name: "",
    phone: "",
    email: "",
    password: "",
    accountType: "EV_OWNER",
    companyName: "",
    registrationNumber: "",
  });

  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const { name, value } = event.target;

    setFormData((current) => ({
      ...current,
      [name]: value,
    }));

    setError("");
  };

  const getRegistrationRequest = (): { endpoint: string; payload: object } => {
    switch (formData.accountType) {
      case "EV_OWNER":
        return {
          endpoint: "/auth/register/user",
          payload: {
            fullName: formData.name,
            email: formData.email,
            password: formData.password,
            phone: formData.phone,
          },
        };

      case "LANDOWNER":
        return {
          endpoint: "/auth/register/host",
          payload: {
            fullName: formData.name,
            email: formData.email,
            password: formData.password,
            phone: formData.phone,
          },
        };

      case "COMPANY_ADMIN":
        return {
          endpoint: "/auth/register/company",
          payload: {
            companyName: formData.companyName,
            registrationNumber: formData.registrationNumber,
            adminEmail: formData.email,
            adminPassword: formData.password,
            adminFullName: formData.name,
            supportPhone: formData.phone,
          },
        };

      default:
        throw new Error("Unsupported account type");
    }
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!formData.name || !formData.phone || !formData.email || !formData.password) {
      setError("Please fill in all required fields.");
      return;
    }

    try {
      setIsSubmitting(true);
      setError("");

      const { endpoint, payload } = getRegistrationRequest();
      const auth = await apiRequest<AuthData>(endpoint, {
        method: "POST",
        body: JSON.stringify(payload),
      });

      saveAuthSession(auth);
      onRegistered(auth);
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Unable to create your account. Please try again."
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="margdarshi-register-page">
      {/* Background Split Overlay */}
      <div className="split-bg-left" />
      <div className="split-bg-right" />

      {/* Main Centered White Card Container */}
      <div className="margdarshi-card">
        {/* LEFT COLUMN: SIGN UP FORM */}
        <section className="form-column">
          <div className="form-header">
            <h1 className="signup-title">Sign up</h1>
            <p className="signup-subtitle">Create your account</p>
          </div>

          {/* Account Role Selector */}
          <div className="role-selector">
            <button
              type="button"
              className={`role-btn ${formData.accountType === "EV_OWNER" ? "active" : ""}`}
              onClick={() => setFormData((c) => ({ ...c, accountType: "EV_OWNER" }))}
            >
              EV Owner
            </button>
            <button
              type="button"
              className={`role-btn ${formData.accountType === "LANDOWNER" ? "active" : ""}`}
              onClick={() => setFormData((c) => ({ ...c, accountType: "LANDOWNER" }))}
            >
              Host
            </button>
            <button
              type="button"
              className={`role-btn ${formData.accountType === "COMPANY_ADMIN" ? "active" : ""}`}
              onClick={() => setFormData((c) => ({ ...c, accountType: "COMPANY_ADMIN" }))}
            >
              Company
            </button>
          </div>

          <form onSubmit={handleSubmit} className="signup-form">
            <div className="input-field-group">
              <input
                id="name"
                name="name"
                type="text"
                placeholder="Name"
                value={formData.name}
                onChange={handleChange}
                required
              />
            </div>

            <div className="input-field-group">
              <input
                id="phone"
                name="phone"
                type="tel"
                placeholder="Phone Number"
                value={formData.phone}
                onChange={handleChange}
                maxLength={10}
                required
              />
            </div>

            <div className="input-field-group">
              <input
                id="email"
                name="email"
                type="email"
                placeholder="Email"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>

            {formData.accountType === "COMPANY_ADMIN" && (
              <>
                <div className="input-field-group">
                  <input
                    id="companyName"
                    name="companyName"
                    type="text"
                    placeholder="Company Name"
                    value={formData.companyName}
                    onChange={handleChange}
                    required
                  />
                </div>
                <div className="input-field-group">
                  <input
                    id="registrationNumber"
                    name="registrationNumber"
                    type="text"
                    placeholder="Registration Number / CIN"
                    value={formData.registrationNumber}
                    onChange={handleChange}
                    required
                  />
                </div>
              </>
            )}

            <div className="input-field-group">
              <input
                id="password"
                name="password"
                type="password"
                placeholder="Password"
                value={formData.password}
                onChange={handleChange}
                minLength={8}
                required
              />
            </div>

            {error && <p className="signup-error-msg">{error}</p>}

            <button
              type="submit"
              className="submit-signup-btn"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Signing Up..." : "Sign Up"}
            </button>

            <p className="already-account">
              Already have an account?{" "}
              <a
                href="#login"
                className="login-link-btn"
                onClick={(e) => {
                  e.preventDefault();
                  onLogin();
                }}
              >
                Login
              </a>
            </p>
          </form>
        </section>

        {/* RIGHT COLUMN: BRANDING & SOCIAL CONNECT */}
        <section className="brand-column">
          <div className="top-brand-title">
            <span className="brand-badge-pill"> VIDYUT</span>
          </div>

          <div className="brand-logo-section">
            <VidyutLogo />

            <div className="brand-feature-badges">
              <span className="feature-pill">⚡ Fast Charging</span>
              <span className="feature-pill">🔒 Safe & Encrypted</span>
              <span className="feature-pill">🌱 Zero Emissions</span>
            </div>
          </div>

          <div className="brand-social-section">
            <div className="divider">
              <hr />
              <span>or continue with</span>
              <hr />
            </div>

            <div className="socialconnect">
              <a href="#google" className="social-icon-box" title="Sign up with Google" onClick={(e) => e.preventDefault()}>
                <svg width="20" height="20" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                </svg>
              </a>

              <a href="#facebook" className="social-icon-box" title="Sign up with Facebook" onClick={(e) => e.preventDefault()}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="#1877F2">
                  <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
                </svg>
              </a>

              <a href="#apple" className="social-icon-box" title="Sign up with Apple" onClick={(e) => e.preventDefault()}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="#000000">
                  <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M15.97 6.63c.66-.8 1.12-1.92.99-3.03-.96.04-2.14.64-2.83 1.44-.61.71-1.14 1.86-.99 2.96 1.08.08 2.18-.56 2.83-1.37z" />
                </svg>
              </a>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
