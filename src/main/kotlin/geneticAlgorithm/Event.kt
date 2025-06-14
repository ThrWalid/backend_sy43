enum class Level(val value: Int){
    Zero(0),
    Low(1),
    Medium(2),
    High(3)
}

class Event(var id: Int, // Independent id, identifying the event in the system (not in the Genetic Algorithm)
            var name: String, // Textual description of the event
            var duration: Int, // Number of timeslots, this event occupies
            var fixedTime: Int, // Specific time, at which the event has to start (applicable only to non-splittable events), if there is no fixed time, the value is -1
            var happensBeforeTask: Int, // The id of event, which has to happen after this event, if this requirement do not apply, the value is -1
            var happensBeforeTime: Int, // The timeslot, before which this event has to happen, if this requirement do not apply, the value is -1
            var difficulty: Level, // The difficulty level, arbitrary hierarchy of tasks
            var priority: Level, // The priority level, arbitrary hierarchy of tasks
            var needsBreak: Boolean = false, // Defines if the user, prefers to at least 1 timeslot of free time after this event
)