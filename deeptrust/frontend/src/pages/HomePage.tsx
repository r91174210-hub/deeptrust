export default function HomePage() {
  return (
    <div className="min-h-screen flex items-center justify-center text-center px-4">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">DeepTrust</h1>
        <p className="text-white/50 mt-2 max-w-md">
          Multimodal Deepfake Authentication and Blockchain Verification Framework.
          Scan a certificate's QR code, or visit{" "}
          <code className="text-deeptrust-accent">/verify/&lt;certificateCode&gt;</code> directly.
        </p>
      </div>
    </div>
  );
}
