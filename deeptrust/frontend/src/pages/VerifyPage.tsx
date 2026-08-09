import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { fetchPublicVerification, VerificationResult } from "../services/api";

/**
 * Feature #7: Public QR verification page.
 * No login required — anyone scanning a certificate's QR code lands here
 * and sees a live status pulled from both MySQL and the blockchain.
 */
export default function VerifyPage() {
  const { certificateCode } = useParams<{ certificateCode: string }>();
  const [result, setResult] = useState<VerificationResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!certificateCode) return;
    setLoading(true);
    fetchPublicVerification(certificateCode)
      .then(setResult)
      .catch(() => setError("Unable to reach the verification service. Please try again shortly."))
      .finally(() => setLoading(false));
  }, [certificateCode]);

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md bg-deeptrust-panel rounded-2xl shadow-xl p-8 border border-white/10">
        <header className="mb-6 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">DeepTrust Verification</h1>
          <p className="text-sm text-white/50 mt-1">Certificate {certificateCode}</p>
        </header>

        {loading && <LoadingState />}
        {!loading && error && <ErrorState message={error} />}
        {!loading && !error && result && <ResultState result={result} />}
      </div>
    </div>
  );
}

function LoadingState() {
  return (
    <div className="flex flex-col items-center gap-3 py-8">
      <div className="h-8 w-8 border-2 border-deeptrust-accent border-t-transparent rounded-full animate-spin" />
      <p className="text-white/60 text-sm">Checking registry and blockchain…</p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="text-center py-6">
      <div className="text-deeptrust-danger text-4xl mb-3">⚠</div>
      <p className="text-white/70 text-sm">{message}</p>
    </div>
  );
}

function ResultState({ result }: { result: VerificationResult }) {
  const isRevoked = result.certificateStatus === "REVOKED";
  const isActive = result.certificateStatus === "ACTIVE";
  const notFound = !result.certificateStatus;

  const statusColor = notFound
    ? "text-white/50"
    : isRevoked
      ? "text-deeptrust-danger"
      : result.chainConfirmed
        ? "text-deeptrust-success"
        : "text-deeptrust-warning";

  return (
    <div>
      <div className="flex flex-col items-center text-center mb-6">
        <StatusIcon notFound={notFound} isRevoked={isRevoked} isActive={isActive} chainConfirmed={result.chainConfirmed} />
        <p className={`mt-3 font-semibold text-lg ${statusColor}`}>
          {notFound ? "Certificate Not Found" : isRevoked ? "Certificate Revoked" : "Certificate Active"}
        </p>
        <p className="text-sm text-white/60 mt-1">{result.message}</p>
      </div>

      {!notFound && (
        <dl className="space-y-3 text-sm border-t border-white/10 pt-4">
          <Row label="AI Verdict" value={result.aiResult ?? "—"} />
          <Row label="Confidence" value={`${(result.confidence * 100).toFixed(1)}%`} />
          <Row label="On-chain confirmed" value={result.chainConfirmed ? "Yes" : "No — check manually"} />
          {result.blockchainTxHash && (
            <Row label="Blockchain Tx" value={truncateHash(result.blockchainTxHash)} mono />
          )}
          {result.certificateIssuedAt && (
            <Row label="Issued" value={new Date(result.certificateIssuedAt).toLocaleString()} />
          )}
        </dl>
      )}

      <p className="text-xs text-white/30 text-center mt-6">
        This page performs a read-only, independent cross-check between DeepTrust's
        database and its on-chain registry. No login required.
      </p>
    </div>
  );
}

function StatusIcon({
  notFound,
  isRevoked,
  chainConfirmed,
}: {
  notFound: boolean;
  isRevoked: boolean;
  isActive: boolean;
  chainConfirmed: boolean;
}) {
  if (notFound) return <div className="text-5xl">❓</div>;
  if (isRevoked) return <div className="text-5xl">✕</div>;
  return <div className="text-5xl">{chainConfirmed ? "✓" : "⚠"}</div>;
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-white/50">{label}</dt>
      <dd className={`text-right ${mono ? "font-mono text-xs" : ""}`}>{value}</dd>
    </div>
  );
}

function truncateHash(hash: string): string {
  return hash.length > 14 ? `${hash.slice(0, 8)}…${hash.slice(-6)}` : hash;
}
