import SystemPage from "@/components/system/SystemPage";
import BootstrapActivateClient from "./BootstrapActivateClient";

export default function BootstrapActivatePage() {
  return (
    <SystemPage
      title="Initial setup"
      description="This application has not been initialized yet."
    >
      <BootstrapActivateClient />
    </SystemPage>
  );
}