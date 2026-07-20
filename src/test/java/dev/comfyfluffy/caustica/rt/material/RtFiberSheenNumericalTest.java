package dev.comfyfluffy.caustica.rt.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CPU quadrature of the fixed Charlie configuration used by world.rgen.slang. */
final class RtFiberSheenNumericalTest {
    @Test
    void fixedCharlieIsReciprocalFiniteAndWhiteFurnaceBounded() {
        for (double noV : new double[]{0.02, 0.05, 0.1, 0.25, 0.5, 0.75, 0.95, 1.0}) {
            double energy = integrate(noV, 240, 480);
            assertTrue(Double.isFinite(energy), "finite at NoV=" + noV);
            assertTrue(energy >= 0.0 && energy <= 1.0 + 2.0e-3,
                    "white-furnace energy=" + energy + " at NoV=" + noV);
        }

        Vec wi = direction(0.37, 0.61);
        Vec wo = direction(0.81, 2.14);
        assertEquals(charlie(wi, wo), charlie(wo, wi), 1.0e-12);
    }

    @Test
    void convexMixtureCannotExceedItsBoundedLobes() {
        for (double weight : new double[]{0.0, 0.1, 0.3, 0.5}) {
            for (double noV : new double[]{0.02, 0.1, 0.5, 1.0}) {
                double fiber = integrate(noV, 160, 320);
                double eonWhiteFurnace = 1.0; // energy-preserving substrate at unit albedo
                double mixture = (1.0 - weight) * eonWhiteFurnace + weight * fiber;
                assertTrue(mixture <= eonWhiteFurnace + 2.0e-3);
            }
        }
    }

    private static double integrate(double noV, int thetaSteps, int phiSteps) {
        Vec wo = new Vec(Math.sqrt(Math.max(0.0, 1.0 - noV * noV)), 0.0, noV);
        double dTheta = 0.5 * Math.PI / thetaSteps;
        double dPhi = 2.0 * Math.PI / phiSteps;
        double sum = 0.0;
        for (int t = 0; t < thetaSteps; t++) {
            double theta = (t + 0.5) * dTheta;
            double sinTheta = Math.sin(theta);
            double noI = Math.cos(theta);
            for (int p = 0; p < phiSteps; p++) {
                Vec wi = direction(noI, (p + 0.5) * dPhi);
                sum += charlie(wi, wo) * noI * sinTheta * dTheta * dPhi;
            }
        }
        return sum;
    }

    private static double charlie(Vec wi, Vec wo) {
        double noI = Math.max(wi.z, 0.0);
        double noO = Math.max(wo.z, 0.0);
        if (noI <= 0.0 || noO <= 0.0) return 0.0;
        Vec h = wi.add(wo).normalized();
        double noH = Math.max(0.0, Math.min(1.0, h.z));
        double d = 1.5 / Math.PI * Math.sqrt(Math.max(1.0 - noH * noH, 0.0));
        double v = 0.25 / Math.max(noI + noO - noI * noO, 1.0e-5);
        return d * v;
    }

    private static Vec direction(double noV, double phi) {
        double sin = Math.sqrt(Math.max(0.0, 1.0 - noV * noV));
        return new Vec(sin * Math.cos(phi), sin * Math.sin(phi), noV);
    }

    private record Vec(double x, double y, double z) {
        Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        Vec normalized() {
            double inv = 1.0 / Math.sqrt(x * x + y * y + z * z);
            return new Vec(x * inv, y * inv, z * inv);
        }
    }
}
