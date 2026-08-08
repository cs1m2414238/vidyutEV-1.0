import React, { useEffect, useState } from "react";
import loadingBgImg from "../assets/loadingpage.png";
import "../css/splash.css";

interface SplashScreenProps {
  onComplete: () => void;
}

export const SplashScreen: React.FC<SplashScreenProps> = ({ onComplete }) => {
  const [isFadingOut, setIsFadingOut] = useState(false);

  useEffect(() => {
    // Start fade out at 1.8 seconds (1800ms)
    const fadeTimer = setTimeout(() => {
      setIsFadingOut(true);
    }, 1800);

    // Call completion handler at 2.1 seconds (2100ms)
    const completeTimer = setTimeout(() => {
      onComplete();
    }, 2100);

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(completeTimer);
    };
  }, [onComplete]);

  return (
    <div className={`splash-screen ${isFadingOut ? "fade-out" : ""}`}>
      {/* EV + Charger full screen background */}
      <div className="splash-bg-wrapper">
        <img
          src={loadingBgImg}
          alt="Vidyut EV Charger Background"
          className="splash-bg-image"
        />
        <div className="splash-overlay" />
      </div>

      {/* Centered cinematic content layout */}
      <div className="splash-content">
        {/* V logo with glowing filter */}
        <div className="splash-logo-container">
          <svg
            className="splash-logo-svg"
            viewBox="0 0 200 200"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <defs>
              <linearGradient
                id="splashVGradient"
                x1="0%"
                y1="0%"
                x2="100%"
                y2="100%"
              >
                <stop offset="0%" stopColor="#d9f99d" />
                <stop offset="25%" stopColor="#a3e635" />
                <stop offset="60%" stopColor="#22c55e" />
                <stop offset="100%" stopColor="#15803d" />
              </linearGradient>
              <filter id="splashVGlow" x="-30%" y="-30%" width="160%" height="160%">
                <feGaussianBlur stdDeviation="8" result="blur" />
                <feComposite in="SourceGraphic" in2="blur" operator="over" />
              </filter>
            </defs>
            <path
              d="M 38,30 L 65,36 L 100,165 L 128,75 L 114,80 L 165,15 L 142,58 L 158,58 L 100,185 Z"
              fill="url(#splashVGradient)"
              filter="url(#splashVGlow)"
            />
          </svg>
        </div>

        {/* VIDYUT Title */}
        <h1 className="splash-title">VIDYUT</h1>

        {/* Tagline */}
        <p className="splash-tagline">Powering a Smarter Tomorrow</p>

        {/* Animated charging line: ⚡ ━━━━━━━━━━━ */}
        <div className="splash-line-wrapper">
          <span className="splash-bolt">⚡</span>
          <div className="splash-line-track">
            <div className="splash-line-fill" />
          </div>
        </div>
      </div>
    </div>
  );
};

export default SplashScreen;
