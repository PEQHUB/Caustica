# Caustica Dimension-Specific Atmosphere Controls

## Status: COMPLETE
All 12 steps implemented and review corrections applied.

## Completed
- [x] Step 1: Preflight checks (bit 15 free, push fields available, skyPush record location)
- [x] Step 2: AtmosphereDimension.java - dimension model enum with resolve/editorDefault/isSimpleGradient
- [x] Step 3: NetherAtmosphere/EndAtmosphere nested static holders in CausticaConfig.Rt.Composite
- [x] Step 4: 18 controls (9 per dimension) and 4 sections in SettingsCatalog
- [x] Step 5: Dimension selector buttons, panels, addSimpleDimensionAtmosphere in CausticaSettingsScreen
- [x] Step 6: Search routing switches dimension tab based on control ID prefix
- [x] Step 7: skyPush() builds effective values before SkyPush construction; atmosphereFrameFlags()
- [x] Step 8: world.rmiss.slang dimension atmosphere gradient with lower-horizon protection
- [x] Step 9: en_us.json translations for all controls, bundles, dimension selectors
- [x] Step 10: AtmosphereDimensionTest, AtmosphereDimensionCustomKeyTest, DimensionAtmosphereCatalogTest

## Review Corrections Applied
- [x] Config: nested static holders (NetherAtmosphere, EndAtmosphere) instead of instance wrapper
- [x] No AMBIENT_LIGHT_EV in V1 dimension scope
- [x] SimpleDimensionAtmosphere record for push construction
- [x] skyPush() builds effective values inline (no post-hoc patching)
- [x] Separated rendered-sky vs physical-LUT signature invalidation
- [x] Lower-horizon protection for dimension branch in rmiss shader
- [x] pendingAtmosphereContentScroll flag for dimension tab switching

## Remaining
- [ ] Build verification (requires Slang 2026.13 compiler)
- [ ] Runtime manual verification in all three vanilla dimensions
