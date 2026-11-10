import { useLocation } from "react-router";
import { SiteHeader, SiteFooter } from "../../home/HomePage";
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
