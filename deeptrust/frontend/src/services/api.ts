import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export interface VerificationResult {
  exactMatch: boolean;
  perceptualMatch: boolean;
  hammingDistance: number;
  certificateCode: string | null;
  certificateStatus: "ACTIVE" | "REVOKED" | null;
  aiResult: "AUTHENTIC" | "MANIPULATED" | "INCONCLUSIVE" | null;
  confidence: number;
  blockchainTxHash: string | null;
  chainConfirmed: boolean;
  certificateIssuedAt: string | null;
  message: string;
}

/** Public, no-auth lookup — powers the QR-code verification page. */
export async function fetchPublicVerification(certificateCode: string): Promise<VerificationResult> {
  const response = await axios.get<VerificationResult>(
    `${API_BASE_URL}/api/public/verify/${encodeURIComponent(certificateCode)}`
  );
  return response.data;
}

export function qrCodeImageUrl(certificateCode: string): string {
  return `${API_BASE_URL}/api/public/qr/${encodeURIComponent(certificateCode)}`;
}
