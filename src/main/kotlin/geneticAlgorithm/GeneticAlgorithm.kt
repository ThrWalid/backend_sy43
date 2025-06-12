package geneticAlgorithm

import java.util.*
import kotlin.random.Random

class GeneticAlgorithm {
    companion object {
        // Genetic Operators
        /**
         * The function performing the One-point Mutation with pMutation probability, by choosing random task from the list of all tasks.
         * If mutation does not occur, returns the original chromosome of the task.
         */
        private fun Mutate(task: Int, taskNum: Int, pMutation: Float, incrementMutation: () -> Unit): Int{
            var randomOtherTask = task

            if (Random.nextFloat() <= pMutation){ // We check if mutation should occur
                randomOtherTask = Random.nextInt(0, taskNum)   // We get any random task in its place
                incrementMutation()
            }

            return randomOtherTask
        }
        /**
         * The function performing the One-point Crossover with pCrossover probability, by choosing random site in the length of the parent chromosome,
         * then constructing the child chromosome from the elements of the first parent UP to the chosen site and the rest of elements from the second parent.
         * If crossover does not occur, all values of child chromosomes are copied from the first parent.
         */
        private fun Crossover(parent1: MutableList<Int>, parent2: MutableList<Int>, taskNum: Int, pCrossover: Float, pMutation: Float, incrementCrossover: () -> Unit, incrementMutation: () -> Unit): MutableList<Int>{
            val length = parent1.count() - 1
            val child = mutableListOf<Int>()

            // We choose the crossing point (if crossing does not occur, we just set it to the chromosome of last position)
            var crossingSite = length - 1

            if (Random.nextFloat() <= pCrossover){
                crossingSite = Random.nextInt(1, length - 1)
                incrementCrossover()
            }

            // We copy first part of elements from the first parent (all elements in case of no crossover)
            for(i in 1..crossingSite){
                child.add(Mutate(parent1[i - 1], taskNum, pMutation, incrementMutation))
            }

            // We copy second part of elements from the second parent (no elements in case of no crossover)
            for(j in crossingSite ..length){
                child.add(Mutate(parent2[j], taskNum, pMutation, incrementMutation))
            }

            return child
        }
        /**
         * The function performing the Roulette-Wheel Selection, by choosing from given population 2 random individuals (not necessarily different),
         * with the chances favouring the individuals with higher fitness.
         * The function returns the array with the id of the first and second parents.
         */
        private fun Select(population: MutableList<Individual>): Array<Int>{
            val parentsIds = arrayOf(0, 0)

            // We sum the total fitness of the whole population
            var totalFitness = 0f

            for (individual in population){
                totalFitness += individual.fitness
            }

            // We get the random id for both parents
            for(i in 0..1){
                // We get the random value in range from 0 to totalFitness
                val randomFitness = Random.nextFloat() * totalFitness

                // We find the corresponding parent id
                var sumFitness = 0f
                var randomId = -1

                // We iterate through each individual, adding their fitness to the sum, until the value exceeds the randomly chosen value
                for(j in 0..<population.count()){
                    if(sumFitness >= randomFitness){
                        randomId = j
                        break
                    }

                    sumFitness += population[j].fitness
                }

                // We cover the corner case in which the last element was supposed to be chosen, but wasn't iterated through (we add fitness value to the sum at the end of the loop)
                if(randomId == -1){
                    randomId = population.count() - 1
                }

                parentsIds[i] = randomId
            }

            return parentsIds
        }
        /**
         * The function performing custom Repair genetic operator.
         * It corrects the chromosomes, by ensuring that each task is occurring right number of times.
         * If some task occurs fewer times than it should, choose random free time slot and insert it there.
         * If some task occurs more times than it should choose its first occurrence and set it to free time.
         */
        private fun Repair(chromosome: MutableList<Int>, eventList: MutableList<Event>, incrementRepair: () -> Unit): MutableList<Int>{
            // We fix the positions of tasks of fixed time
            val fixedChromosome = CorrectFixedTime(chromosome, eventList)

            // We generate the random template
            val randomTemplate = GenerateRandomTemplate(chromosome.size, eventList)

            // Correcting the chromosome using the random template
            for(i in 0..<chromosome.size){
                if(!randomTemplate.contains(fixedChromosome[i])){
                    incrementRepair()
                    fixedChromosome[i] = randomTemplate.first()
                }

                randomTemplate.remove(fixedChromosome[i])
            }

            return fixedChromosome
        }


        // Fitness Calculations
        /**
         * Function which calculates the alignment of the individual's schedule with the user preferences.
         * - schedule - mutable list of event id's, reduced representation of the schedule
         * - preferences - object of data class storing user's preferences
         * - eventList - list of the event objects that have to be scheduled
         *
         * Returns the integer value of the chromosome.
         */
        private fun Objective(schedule: MutableList<Int>, preferences: Preferences, eventList: List<Event>, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int, cMax: Int): Int{
            var totalPenalties = 0

            // Expanding the schedule to the full 48-element long representation
            val scheduleExpanded = mutableListOf<Int>()

            for(task in schedule){
                if(task == 0){
                    scheduleExpanded.add(0)
                    continue
                }
                // We check how many timeslots the task should take and insert that many task id's into its expanded version
                else if(!eventList[task].canSeparate) {
                    for (j in 0..<eventList[task].duration) {
                        scheduleExpanded.add(task)
                    }
                }
                else{
                    scheduleExpanded.add(task)
                }
            }

            // Grading the schedule by the requirements of individual tasks
            val finishedEvents = mutableListOf<Int>() // List of event id's, which are already finished

            for(j in 0 ..<scheduleExpanded.count()){
                // Grading the schedule according to the distribution of free time throughout the day
                if(scheduleExpanded[j] != 0 && preferences.freeTimeDistribution[j]) {
                    totalPenalties += mediumPenalty
                }

                val currentTask = eventList[scheduleExpanded[j]]

                // Grading the schedule according to the breaks after tiring events
                if(j != (scheduleExpanded.count() - 1)){
                    if(currentTask.needsBreak && scheduleExpanded[j + 1] == 0){
                        totalPenalties += lowPenalty
                    }
                }

                // Grading the schedule according to the relation "needs to happen before event x"
                if(currentTask.happensBeforeTask > -1){
                    if(finishedEvents.contains(currentTask.happensBeforeTask)){
                        totalPenalties += highPenalty
                    }
                }

                // Grading the schedule according to the relation "needs to happen after event x"
                if(currentTask.happensAfterTask > -1){
                    if(!finishedEvents.contains(currentTask.happensAfterTask)){
                        totalPenalties += highPenalty
                    }
                }

                // Grading the schedule according to the relation "needs to happen before time x"
                if(currentTask.happensBeforeTime > -1){
                    if(j > currentTask.happensBeforeTime){
                        totalPenalties += highPenalty
                    }
                }

                // Grading the schedule according to the relation "needs to happen after time x"
                if(currentTask.happensAfterTime > -1){
                    if(j < currentTask.happensAfterTime){
                        totalPenalties += highPenalty
                    }
                }

                // Creating a list of all already finished task (used for one of the task requirements)
                if(!currentTask.canSeparate && !finishedEvents.contains(currentTask.id)){
                    finishedEvents.add(currentTask.id)
                }
            }

            // Grading the schedule according to the requirements for the overall structure of tasks
            val peakStarts = mutableListOf<Int>()
            val peakEnds = mutableListOf<Int>()
            var onTheHill = false

            val scheduleTasksOnly = mutableListOf<Int>()

            val difficulties = mutableListOf<Int>()
            val priorities = mutableListOf<Int>()

            for(j in 0..<schedule.count()){
                if(scheduleExpanded[j] > 0){
                    scheduleTasksOnly.add(scheduleExpanded[j])
                }

                difficulties.add(eventList[scheduleExpanded[j]].difficulty.value)
                priorities.add(eventList[scheduleExpanded[j]].priority.value)
            }

            // Grading the schedule according to the distribution of the priority
            val maxPriority = Collections.max(priorities)

            for(j in 0..<priorities.count()){
                if(priorities[j] == maxPriority){
                    if(onTheHill){
                        continue
                    }
                    else{
                        onTheHill = true
                        peakStarts.add(j)
                    }
                }
                else{
                    if(onTheHill){
                        peakEnds.add(j)
                        onTheHill = false
                    }
                }
            }

            if(onTheHill){
                peakEnds.add(priorities.count())
            }

            if(peakStarts.size > 1){
                totalPenalties += mediumPenalty
            }
            else{
                if((preferences.priorityOrder == Order.First && peakStarts[0] > 0)
                    || preferences.priorityOrder == Order.Last && peakEnds[0] < scheduleTasksOnly.size
                    || preferences.priorityOrder == Order.Middle && (peakStarts[0] == 0 || peakEnds[0] == scheduleExpanded.size)){
                    totalPenalties += mediumPenalty
                }
            }

            // Grading the schedule according to the distribution of the difficulty
            onTheHill = false
            peakStarts.clear()
            peakEnds.clear()

            val maxDifficulty = Collections.max(difficulties)

            for(j in 0..<difficulties.count()){
                if(difficulties[j] == maxDifficulty){
                    if(onTheHill){
                        continue
                    }
                    else{
                        onTheHill = true
                        peakStarts.add(j)
                    }
                }
                else{
                    if(onTheHill){
                        peakEnds.add(j)
                        onTheHill = false
                    }
                }
            }

            if(onTheHill){
                peakEnds.add(difficulties.count())
            }

            if(peakStarts.size > 1){
                totalPenalties += mediumPenalty
            }
            else{
                if((preferences.difficultyOrder == Order.First && peakStarts[0] > 0)
                    || preferences.difficultyOrder == Order.Last && peakEnds[0] < scheduleTasksOnly.size
                    || preferences.difficultyOrder == Order.Middle && (peakStarts[0] == 0 || peakEnds[0] == scheduleExpanded.size)){
                    totalPenalties += mediumPenalty
                }
            }

            // Grading the schedule according to the grouping of tasks
            var peakNum = 0

            // We keep track of values of tasks
            var prevTask = 0
            var currTask: Int

            // We iterate through the whole schedule, looking for rises in the plot
            for(j in 0..<scheduleExpanded.count()){
                // We change each value task id to 1 (since we care only whether there is a task or not)
                currTask = if (scheduleExpanded[j] > 0) { 1 } else { 0 }

                if(prevTask < currTask){
                    peakNum += 1
                }

                prevTask = currTask
            }

            // We add value to the fitness score if the number of peaks matches the user's preferences
            if(preferences.groupingStyle == peakNum){
                totalPenalties += mediumPenalty
            }

            return cMax - totalPenalties
        }
        /**
         * Function which calculates the fitness value of an individual.
         * Distinct from the objective value, to simplify possible modifications to the final score of the individual.
         */
        private fun Fitness(value: Int): Int{
            return value * value
        }

        // Creating new generations
        /**
         * Function creating new population of individuals, based on the previous population.
         * It chooses 2 individuals to create offspring popSize/2 times, by applying genetic operators.
         * - oldPop - previous population of individuals
         * - popSize - size of the generated population
         * - pCrossover - float value representing probability of crossover occurring
         * - pMutation - float value representing probability of mutation occurring
         * - preferences - object of data class storing user's preferences
         * - eventList - list of the event objects that have to be scheduled
         *
         * Returns the mutable list of individuals, representing new population.
         */
        private fun GenerateNextPop(oldPop: MutableList<Individual>, popSize: Int, pCrossover: Float, pMutation: Float, preferences: Preferences, eventList: MutableList<Event>, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int, cMax: Int, incrementCrossover: () -> Unit = {}, incrementMutation: () -> Unit = {}, incrementRepair: () -> Unit = {}): MutableList<Individual>{
            val newPop = mutableListOf<Individual>()

            while(newPop.size < popSize){
                val parentsIds = Select(oldPop)

                val parent1 = oldPop[parentsIds[0]]
                val parent2 = oldPop[parentsIds[1]]

                val childChromosome1 = Crossover(parent1.chromosome, parent2.chromosome, eventList.size, pCrossover, pMutation, incrementCrossover, incrementMutation)
                val childChromosome2 = Crossover(parent1.chromosome, parent2.chromosome, eventList.size, pCrossover, pMutation, incrementCrossover, incrementMutation)

                val child1 = Individual()

                child1.chromosome = Repair(childChromosome1, eventList, incrementRepair)
                child1.value = Objective(child1.chromosome, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)
                child1.fitness = Fitness(child1.value)
                child1.parent1 = parentsIds[0]
                child1.parent2 = parentsIds[1]

                val child2 = Individual()


                child2.chromosome = Repair(childChromosome2, eventList, incrementRepair)
                child2.value = Objective(child2.chromosome, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)
                child2.fitness = Fitness(child2.value)
                child2.parent1 = parentsIds[0]
                child2.parent2 = parentsIds[1]

                newPop.add(child1)
                newPop.add(child2)
            }

            return newPop
        }
        /**
         * Function creating new population with randomly ordered schedules.
         * - popSize - size of the generated population
         * - chromosomeLength - the size of generated chromosomes for the individuals
         * - preferences - object of data class storing user's preferences
         * - eventList - list of the event objects that have to be scheduled
         *
         * Returns the mutable list of individuals.
         */
        private fun GenerateRandomPop(popSize: Int, chromosomeLength: Int, preferences: Preferences, eventList: MutableList<Event>, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int, cMax: Int, incrementRepair: () -> Unit = {}): MutableList<Individual>{
            val randomPop = mutableListOf<Individual>()

            // Creating new individual with random chromosome to fill initial population
            for(i in 0..<popSize){
                val chromosome = mutableListOf<Int>()

                // Adding all possible task in proper number
                for(j in 1..<eventList.size){
                    if(eventList[j].canSeparate){
                        for(k in 0..<eventList[j].duration){
                            chromosome.add(j)
                        }
                    }
                    else{
                        chromosome.add(j)
                    }
                }

                // Filling all remaining spaces with free time
                for(j in 0..<(chromosomeLength - chromosome.size)){
                    chromosome.add(0)
                }

                // Randomizing positions of all tasks
                chromosome.shuffle()


                // Creating new individual with given parameters and adding them to the population
                val newIndividual = Individual()

                newIndividual.chromosome = Repair(chromosome, eventList, incrementRepair)
                newIndividual.value = Objective(newIndividual.chromosome, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)
                newIndividual.fitness = Fitness(newIndividual.value)

                randomPop.add(newIndividual)
            }

            return randomPop
        }

        // Utility functions
        private fun GenerateRandomTemplate(chromosomeLength: Int, eventList: MutableList<Event>): MutableList<Int>{
            val scheduleTemplate = mutableListOf<Int>()

            for(i in 1..<eventList.size){
                if(eventList[i].canSeparate){
                    for(j in 0..<eventList[i].duration){
                        scheduleTemplate.add(i)
                    }
                }
                else{
                    scheduleTemplate.add(i)
                }
            }

            for(i in scheduleTemplate.size..<chromosomeLength){
                scheduleTemplate.add(0)
            }

            scheduleTemplate.shuffle()

            return scheduleTemplate
        }
        private fun CorrectFixedTime(chromosome: MutableList<Int>, eventList: MutableList<Event>): MutableList<Int>{
            for(i in 0..<eventList.size){
                if(eventList[i].fixedTime >= 0){
                    chromosome.replaceAll{ if (it == i) 0 else it }

                    var accumulatedGap = CalculateChromosomeGap(i, chromosome, eventList)

                    chromosome[eventList[i].fixedTime - accumulatedGap] = i
                }
            }

            return chromosome
        }
        private fun CalculateChromosomeGap(eventId: Int, chromosome: MutableList<Int>, eventList: MutableList<Event>): Int{
            var accumulatedGap = 0
            var chromosomeExpanded = mutableListOf<Int>()

            // Expanding the schedule
            for(i in 0..<chromosome.size){
                if(!eventList[chromosome[i]].canSeparate){
                    chromosomeExpanded.add(chromosome[i])

                    for(j in 1..<eventList[chromosome[i]].duration){
                        chromosomeExpanded.add(0)
                    }
                }
                else{
                    chromosomeExpanded.add(chromosome[i])
                }
            }

            // Calculating the gap
            for(i in 0..<eventList[eventId].fixedTime){
                if(!eventList[chromosomeExpanded[i]].canSeparate){
                    accumulatedGap += eventList[chromosomeExpanded[i]].duration - 1
                }
            }

            return accumulatedGap
        }
        /**
         * Function which calculates the maximum value of all penalties combined, that can be put on a schedule.
         * Allows for easier Genetic Search (since we have to minimise a function of penalties).
         * - eventList - list of the event objects that have to be scheduled
         * - preferences - object of data class storing user's preferences
         * - lowPenalty - integer value of penalty, applied when breaking restrictions of low importance
         * - MediumPenalty - integer value of penalty, applied when breaking restrictions of medium importance
         * - HighPenalty - integer value of penalty, applied when breaking restrictions of high importance
         */
        private fun GetMaxPenalty(eventList: MutableList<Event>, preferences: Preferences, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int): Int{
            var cMax = 0 // Maximum value of all combined penalties

            cMax += mediumPenalty // Distribution of tasks according to priority
            cMax += mediumPenalty // Distribution of tasks according to difficulty

            cMax += mediumPenalty // Grouping of tasks

            for (event in eventList){
                // Rest after task
                if(event.needsBreak){
                    cMax += if(event.canSeparate){
                        lowPenalty * event.duration
                    } else{
                        lowPenalty
                    }
                }

                // Before/After task preferences
                if(event.happensAfterTask >= 0 || event.happensBeforeTask >= 0){
                    cMax += highPenalty
                }

                // Before/After time preferences
                if(event.happensAfterTime >= 0 || event.happensBeforeTime >= 0){
                    cMax += highPenalty
                }
            }

            // Free time preferences
            for(slot in preferences.freeTimeDistribution){
                if(slot){
                    cMax += mediumPenalty
                }
            }

            return cMax
        }
        private fun PrintGenerationReport(oldPop: MutableList<Individual>, newPop: MutableList<Individual>, chromosomeLength: Int, generation: Int, lowFitness: Int, highFitness: Int, sumFitness: Int, avgFitness: Float, crossoverCount: Int, mutationCount: Int, repairCount: Int){
            val termWidth = 345
            val title = "GENERATION #" + generation.toString() + " -> #" + (generation+1).toString()
            println("\n" + title.center(termWidth) + "\n")

            // Headers
            val headerFormat = "| %-5s | %-8s | %-8s | %-${chromosomeLength * 2.9 + 6}s | %-10s | %-12s |"
            val header = String.format(
                headerFormat,
                "ID",
                "Parent 1",
                "Parent 2",
                "Chromosome",
                "Value",
                "Fitness Score"
            )
            val separator = "-".repeat(header.length)

            println("$header   $header")
            println("$separator   $separator")

            // Print individuals side by side
            val maxRows = maxOf(oldPop.size, newPop.size)
            for (i in 0..<maxRows) {
                val left = oldPop[i].toRow(i, chromosomeLength)
                val right = newPop[i].toRow(i, chromosomeLength)
                println("$left   $right")
            }

            println("\nStats:")
            println("Lowest Fitness : $lowFitness")
            println("Highest Fitness: $highFitness")
            println("Sum Fitness: $sumFitness")
            println("Mean Fitness   : ${"%.4f".format(avgFitness)}")
            println("Mutation Count : $mutationCount")
            println("Crossover Count: $crossoverCount")
            println("Repair Count   : $repairCount")
        }
        private fun Individual.toRow(id: Int, chromosomeLength: Int): String {
            return String.format(
                "| %-5d | %-8s | %-8s | %-${chromosomeLength}s | %-10d | %-12d  |",
                id,
                parent1.toString(),
                parent2.toString(),
                chromosome,
                value,
                fitness
            )
        }

        private fun String.center(width: Int): String {
            val padSize = (width - this.length).coerceAtLeast(0)
            val padStart = padSize / 2
            val padEnd = padSize - padStart
            return " ".repeat(padStart) + this + " ".repeat(padEnd)
        }

        // API
        fun ShowcaseAlgorithm(){
            // Example schedule
            val event1 = Event(1, "SY43 Lecture", 4, 15, -1, -1, -1, -1, Level.Low, Level.Medium, false, false)
            val event2 = Event(2, "IT41 Lecture", 4, 19, -1, -1, -1, -1, Level.Medium, Level.Medium, true, false)
            val event3 = Event(3, "IT41 TP session", 3, 32, -1, -1, -1, -1, Level.Medium, Level.High, true, false)
            val event4 = Event(4, "Cook dinner", 2, -1, 3, -1, -1, -1, Level.Low, Level.Medium, false, false)
            val event5 = Event(5, "Grocery shopping", 2, -1, 4, -1, -1, -1, Level.Low, Level.High, true, false)
            val event6 = Event(6, "Clean dishes", 1, -1, -1, 4, -1, -1, Level.Low, Level.Low, false, true)
            val event7 = Event(7, "Exercise", 3, -1, -1, 3, -1, -1, Level.Low, Level.High, true, false)
            val event8 = Event(8, "LE05 homework", 2, -1, -1, -1, -1, -1, Level.Low, Level.Low, false, true)
            val event9 = Event(9, "Text Walid", 1, -1, -1, -1, 32, -1, Level.Low, Level.High, false, true)
            val event10 = Event(10, "Visit CAF office", 2, -1, -1, -1, 17, 33, Level.Low, Level.High, true, false)
            val event11 = Event(11, "Duolingo", 1, -1, -1, -1, -1, -1, Level.Low, Level.Low, false, true)

            val eventList: MutableList<Event> = mutableListOf(event1, event2, event3, event4, event5, event6, event7, event8, event9, event10, event11)


            // Example preferences
            val freeTimeDistribution = mutableListOf<Boolean>()

            for(i in 0..<48){
                when (i) {
                    in 0..14 -> {
                        freeTimeDistribution.add(true)
                    }
                    in 25..26 -> {
                        freeTimeDistribution.add(true)
                    }
                    in 32..34 -> {
                        freeTimeDistribution.add(true)
                    }
                    else -> {
                        freeTimeDistribution.add(false)
                    }
                }
            }
            val preferences = Preferences(Order.Middle, Order.Middle, 4, freeTimeDistribution)

            // Meta parameters
            val lowPenalty = 1
            val mediumPenalty = 5
            val highPenalty = 10

            val maxPop = 50
            val popSize = 100

            val pMutation = 0.01f
            val pCrossover = 0.8f

            val cMax = GetMaxPenalty(eventList, preferences, lowPenalty, mediumPenalty, highPenalty)

            // Creating the "Free time task" and adding it to the event list
            val freeTime = Event(-1, "Free time", 1, -1, -1, -1, -1, -1, Level.Zero, Level.Zero, needsBreak = false, canSeparate = true)
            eventList.add(0, freeTime)

            // Preparing the form of chromosomes (the shortened form of schedules)
            var indivisibleTaskTime = 0

            for(event in eventList){
                if(!event.canSeparate){
                    indivisibleTaskTime += event.duration - 1 // The task duration will be represented by 1 timeslot
                }
            }

            val chromosomeLength = 48 - indivisibleTaskTime

            // Statistical variables
            var lowFitness: Int
            var highFitness: Int
            var sumFitness = 0
            var avgFitness: Float

            var mutationCount = 0
            var crossoverCount = 0
            var repairCount = 0

            val incrementMutation: () -> Unit ={
                mutationCount++
            }

            val incrementCrossover: () -> Unit ={
                crossoverCount++
            }

            val incrementRepair: () -> Unit ={
                repairCount++
            }

            var currentPop = GenerateRandomPop(popSize, chromosomeLength, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)

            for(i in 1..maxPop){
                // Creating new population
                val newPop = GenerateNextPop(currentPop, popSize, pCrossover, pMutation, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax, incrementCrossover, incrementMutation, incrementRepair)

                // Calculating statistics of new population
                lowFitness = cMax
                highFitness = 0

                for(individual in newPop){
                    if(individual.fitness < lowFitness){ lowFitness = individual.fitness}
                    if(individual.fitness > highFitness){ highFitness = individual.fitness}

                    sumFitness += individual.fitness
                }
                avgFitness = (sumFitness).toFloat() / popSize

                // Printing the summary of the populations
                PrintGenerationReport(currentPop, newPop, chromosomeLength, i, lowFitness, highFitness, sumFitness, avgFitness, crossoverCount, mutationCount, repairCount)

                println("Press Enter to continue..")
                readln()

                currentPop = newPop
            }
        }

        /**
         * Function performing the search for the best possible schedules aligning with the user preferences.
         * - eventList - list of the event objects that have to be scheduled
         * - preferences - object of data class storing user's preferences
         * - schedulesNum - number of the best schedules returned to the user
         *
         * Returns top schedulesNum schedules from the final population
         */
        fun GenerateSchedules(eventList: MutableList<Event>, preferences: Preferences, schedulesNum: Int): MutableList<MutableList<Int>>{
            // Meta parameters
            val lowPenalty = 1
            val mediumPenalty = 5
            val highPenalty = 10

            val maxPop = 50
            val popSize = 100

            val pMutation = 0.01f
            val pCrossover = 0.8f

            val cMax = GetMaxPenalty(eventList, preferences, lowPenalty, mediumPenalty, highPenalty)

            // Creating the "Free time task" and adding it to the event list
            val freeTime = Event(-1, "Free time", 1, -1, -1, -1, -1, -1, Level.Zero, Level.Zero, needsBreak = false, canSeparate = true)
            eventList.add(0, freeTime)

            // Preparing the form of chromosomes (the shortened form of schedules)
            var indivisibleTaskTime = 0

            for(event in eventList){
                if(!event.canSeparate){
                    indivisibleTaskTime += event.duration - 1 // The task duration will be represented by 1 timeslot
                }
            }

            val chromosomeLength = 48 - indivisibleTaskTime

            // Performing Genetic Search
            var currentPop = GenerateRandomPop(popSize, chromosomeLength, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)

            for(i in 1..maxPop){
                val newPop = GenerateNextPop(currentPop, popSize, pCrossover, pMutation, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)

                currentPop = newPop
            }

            // Retrieving the best schedules from the final generation
            val retrievedSchedules = mutableListOf<MutableList<Int>>()

            for(i in 0..<schedulesNum){
                val bestSchedule = mutableListOf<Int>()

                // We find best chromosome
                var bestChromosome = currentPop[0].chromosome
                var bestFitness: Int = currentPop[0].fitness
                var bestPosition = 0

                for(j in 1..<currentPop.size){
                    if(bestFitness < currentPop[j].fitness){
                        bestChromosome = currentPop[j].chromosome
                        bestFitness = currentPop[j].fitness
                        bestPosition = j
                    }
                }

                // We expand the chromosome into schedule
                for(eventId in bestChromosome){
                    if(eventList[eventId].canSeparate){
                        bestSchedule.add(eventId)
                    }
                    else{
                        for(j in 0..<eventList[eventId].duration){
                            bestSchedule.add(eventId)
                        }
                    }
                }

                retrievedSchedules.add(bestSchedule)

                // We remove the currently best from population
                currentPop.removeAt(bestPosition)
            }

            return retrievedSchedules
        }
    }
}