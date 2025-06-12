package geneticAlgorithm

class Individual{
    var parent1: Int = -1
    var parent2: Int = -1
    var chromosome: MutableList<Int> = mutableListOf()
    var value: Int = 0
    var fitness: Int = 0
}