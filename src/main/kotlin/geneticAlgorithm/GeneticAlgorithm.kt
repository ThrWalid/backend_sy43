package geneticAlgorithm

import java.util.*
import kotlin.random.Random
import Event

class GeneticAlgorithm {
    companion object {
        // Genetic Operators
        /**
         * The function performing the One-point Mutation with pMutation probability, by choosing random task from the list of all tasks.
         * If mutation does not occur, returns the original chromosome of the task.
         */
        private fun Mutate(task: Int, taskNum: Int, pMutation: Float): Int{
            var randomOtherTask = task

            if (Random.nextFloat() <= pMutation){ // We check if mutation should occur
                randomOtherTask = Random.nextInt(0, taskNum)   // We get any random task in its place
            }

            return randomOtherTask
        }
        /**
         * The function performing the One-point Crossover with pCrossover probability, by choosing random site in the length of the parent chromosome,
         * then constructing the child chromosome from the elements of the first parent UP to the chosen site and the rest of elements from the second parent.
         * If crossover does not occur, all values of child chromosomes are copied from the first parent.
         */
        private fun Crossover(parent1: MutableList<Int>, parent2: MutableList<Int>, taskNum: Int, pCrossover: Float, pMutation: Float): MutableList<Int>{
            val length = parent1.count() - 1
            val child = mutableListOf<Int>()

            // We choose the crossing point (if crossing does not occur, we just set it to the chromosome of last position)
            var crossingSite = length - 1

            if (Random.nextFloat() <= pCrossover){
                crossingSite = Random.nextInt(1, length - 1)
            }

            // We copy first part of elements from the first parent (all elements in case of no crossover)
            for(i in 1..crossingSite){
                child.add(Mutate(parent1[i - 1], taskNum, pMutation))
            }

            // We copy second part of elements from the second parent (no elements in case of no crossover)
            for(j in crossingSite ..length){
                child.add(Mutate(parent2[j], taskNum, pMutation))
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
        private fun Repair(chromosome: MutableList<Int>, eventList: MutableList<Event>): MutableList<Int>{
            // We fix the positions of tasks of fixed time
            val fixedChromosome = CorrectFixedTime(chromosome, eventList)

            // We generate the random template
            val randomTemplate = GenerateRandomTemplate(chromosome.size, eventList)

            // Correcting the chromosome using the random template
            for(i in 0..<chromosome.size){
                if(!randomTemplate.contains(fixedChromosome[i])){
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
                else{
                    for (j in 0..<eventList[task].duration) {
                        scheduleExpanded.add(task)
                    }
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

                // Grading the schedule according to the relation "needs to happen before time x"
                if(currentTask.happensBeforeTime > -1){
                    if(j > currentTask.happensBeforeTime){
                        totalPenalties += highPenalty
                    }
                }

                // Creating a list of all already finished task (used for one of the task requirements)
                if(!finishedEvents.contains(currentTask.id)){
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
        private fun GenerateNextPop(oldPop: MutableList<Individual>, popSize: Int, pCrossover: Float, pMutation: Float, preferences: Preferences, eventList: MutableList<Event>, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int, cMax: Int): MutableList<Individual>{
            val newPop = mutableListOf<Individual>()

            while(newPop.size < popSize){
                val parentsIds = Select(oldPop)

                val parent1 = oldPop[parentsIds[0]]
                val parent2 = oldPop[parentsIds[1]]

                val childChromosome1 = Crossover(parent1.chromosome, parent2.chromosome, eventList.size, pCrossover, pMutation)
                val childChromosome2 = Crossover(parent1.chromosome, parent2.chromosome, eventList.size, pCrossover, pMutation)

                val child1 = Individual()

                child1.chromosome = Repair(childChromosome1, eventList)
                child1.value = Objective(child1.chromosome, preferences, eventList, lowPenalty, mediumPenalty, highPenalty, cMax)
                child1.fitness = Fitness(child1.value)
                child1.parent1 = parentsIds[0]
                child1.parent2 = parentsIds[1]

                val child2 = Individual()


                child2.chromosome = Repair(childChromosome2, eventList)
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
        private fun GenerateRandomPop(popSize: Int, chromosomeLength: Int, preferences: Preferences, eventList: MutableList<Event>, lowPenalty: Int, mediumPenalty: Int, highPenalty: Int, cMax: Int): MutableList<Individual>{
            val randomPop = mutableListOf<Individual>()

            // Creating new individual with random chromosome to fill initial population
            for(i in 0..<popSize){
                val chromosome = mutableListOf<Int>()

                // Adding all possible task in proper number
                for(j in 1..<eventList.size){
                    chromosome.add(j)
                }

                // Filling all remaining spaces with free time
                for(j in 0..<(chromosomeLength - chromosome.size)){
                    chromosome.add(0)
                }

                // Randomizing positions of all tasks
                chromosome.shuffle()


                // Creating new individual with given parameters and adding them to the population
                val newIndividual = Individual()

                newIndividual.chromosome = Repair(chromosome, eventList)
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
                scheduleTemplate.add(i)
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
                chromosomeExpanded.add(chromosome[i])

                for(j in 1..<eventList[chromosome[i]].duration){
                    chromosomeExpanded.add(0)
                }
            }

            // Calculating the gap
            for(i in 0..<eventList[eventId].fixedTime){
                accumulatedGap += eventList[chromosomeExpanded[i]].duration - 1
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
                    cMax += lowPenalty
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
        

        // API
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
            val freeTime = Event(-1, "Free time", 1, -1, -1, -1, Level.Zero, Level.Zero, needsBreak = false)
            eventList.add(0, freeTime)

            // Preparing the form of chromosomes (the shortened form of schedules)
            var totalTasksTime = 0

            for(event in eventList){
                totalTasksTime += event.duration - 1 // The task duration will be represented by 1 timeslot
            }

            val chromosomeLength = 48 - totalTasksTime

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
                    for(j in 0..<eventList[eventId].duration){
                        bestSchedule.add(eventId)
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