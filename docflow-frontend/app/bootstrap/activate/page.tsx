import BootstrapActivateClient from "./BootstrapActivateClient";

export default function BootstrapActivatePage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-black text-white">
      <div className="max-w-md w-full space-y-6 text-center">
        <h1 className="text-2xl font-bold">Initial setup</h1>

        <p className="opacity-80">
          This application has not been initialized yet.
        </p>

        <BootstrapActivateClient />
      </div>
    </div>
  );
}
