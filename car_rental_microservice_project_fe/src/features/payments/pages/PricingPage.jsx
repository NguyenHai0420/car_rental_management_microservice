import { useLocation } from "react-router";
import { SiteHeader } from "../../home/SiteHeader";
import { SiteFooter } from "../../home/SiteFooter";
import { PricingCalculator } from "../components/PricingCalculator";

export const PricingPage = () => {
  const location = useLocation();
  const showroomCar = location.state?.showroomCar || null;

  return (
    <div className="site">
      <SiteHeader />
      <main className="payment-main">
        <PricingCalculator key={showroomCar?.id || "default"} showroomCar={showroomCar} />
      </main>
      <SiteFooter />
    </div>
  );
};
