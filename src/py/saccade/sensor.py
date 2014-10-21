from utility import raw_array_from_indices_of_1

SENSOR_PATTERN_BITS = 18

SENSOR_VALUE_BITS = 9

# Input:
# [[0, 0, 0],
#  [0, 1, 0],
#  [0, 0, 1]]
#
# It gets doubled in size, so that black and white are both expressed with On bits:
# [[[0, 1], [0, 1], [0, 1]],
#  [[0, 1], [1, 0], [0, 1]],
#  [[0, 1], [0, 1], [1, 0]]]
#
# The final sensor pattern is the flattened list:
# [0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 1, 0]
# represented using NuPIC convention:
# [1, 3, 5, 7, 8, 11, 13, 15, 16]

def matrix_to_sensor_pattern(matrix):
    flattened = reduce(lambda x, y: x+y,
                       matrix, [])

    assert len(flattened) == SENSOR_VALUE_BITS, "The sensor value should contain %s bits" % SENSOR_VALUE_BITS

    indices_of_1 = set()
    for i in range(len(flattened)):
        if flattened[i] == 1:
            indices_of_1.add(2*i)
        elif flattened[i] == 0:
            indices_of_1.add(2*i + 1)
        else:
            raise Exception("Invalid value in %s. Specifically: %s. More specifically: %s"\
                                      %(matrix, flattened, flattened[i]))

    return raw_array_from_indices_of_1(indices_of_1, SENSOR_PATTERN_BITS)

def extract_sensor_pattern(sensor_value):
    return matrix_to_sensor_pattern(sensor_value)

def sensor_pattern_to_matrix(sensor_pattern, desired_rows, desired_columns):
    pass
