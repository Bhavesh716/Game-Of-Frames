package com.instantcollabmaker.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Processing : Screen("processing")
    data object Results : Screen("results")
    data object PersonDetail : Screen("person/{personId}") {
        fun createRoute(personId: String) = "person/$personId"
    }
    data object PersonCollage : Screen("person_collage/{personId}") {
        fun createRoute(personId: String) = "person_collage/$personId"
    }
    data object FullCollage : Screen("full_collage")
}
