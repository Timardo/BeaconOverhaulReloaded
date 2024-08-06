package gay.solonovamax.beaconsoverhaul.register

//import gay.solonovamax.beaconsoverhaul.advancement.RedirectBeaconCriterion
import net.minecraft.advancement.criterion.Criteria

object CriterionRegistry : CommonRegistration {
    //val REDIRECT_BEACON_CRITERION = RedirectBeaconCriterion

    override fun register() {
        //Criteria.register("redirect_beacon", REDIRECT_BEACON_CRITERION) disabled because I'm stupid and I don't know how to fix advancements
    }
}
