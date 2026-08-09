import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import "./index.css";
import VerifyPage from "./pages/VerifyPage";
import HomePage from "./pages/HomePage";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        {/* Public QR verification route — no auth required, matches
            deeptrust.public-verify-base-url on the backend */}
        <Route path="/verify/:certificateCode" element={<VerifyPage />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>
);
