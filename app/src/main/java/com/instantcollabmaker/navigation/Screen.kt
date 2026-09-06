package com.instantcollabmaker.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Home : Screen("home")
    data object Processing : Screen("processing")
    data object Results : Screen("results")
    data object PersonDetail : Screen("person/{personId}") {
        fun createRoute(personId: String) = "person/$personId"
    }
    data object PersonCollage : Screen("person_collage/{personId}") {
        fun createRoute(personId: String) = "person_collage/$personId"
    }
    /** The full interactive collage editor (pinch-zoom/pan/swap per tile). Assignment-
     * minimal mode: kept, fully functional, but not reachable from the current minimal
     * flow — see [SimpleCollage] and `AppNavigation`'s comment at the `Results` route. */
    data object FullCollage : Screen("full_collage")
    /** The simplified, presentation-only collage screen the minimal flow's "Create
     * Collage" button actually opens. */
    data object SimpleCollage : Screen("simple_collage")
    data object FullscreenAlbum : Screen("album/{personId}/{appearanceIndex}") {
        fun createRoute(personId: String, appearanceIndex: Int) = "album/$personId/$appearanceIndex"
    }
}
