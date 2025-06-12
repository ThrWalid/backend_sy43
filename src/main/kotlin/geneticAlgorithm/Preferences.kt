package geneticAlgorithm

enum class Order(val value: Int){
    First(1),
    Middle(2),
    Last(3)
}

data class Preferences (var difficultyOrder: Order, // preferences about the distribution of events through the day according to their difficulty level
                        var priorityOrder: Order, // preferences about the distribution of events through the day according to their priority level
                        var groupingStyle: Int, // preferences about the number of 'groups' of tasks during the day
                        var freeTimeDistribution: MutableList<Boolean> // preferences about at which hours user would like to have free time
)