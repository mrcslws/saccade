import numpy


def get_indices_of_1(raw_array):
    indices_of_1 = set()
    for i in range(len(raw_array)):
        if raw_array[i] == 1:
            indices_of_1.add(i)
    return indices_of_1


def raw_array_from_indices_of_1(indices_of_1, length):
    raw_array = numpy.zeros((length,), dtype="int")
    for index in indices_of_1:
        raw_array[index] = 1
    return raw_array
