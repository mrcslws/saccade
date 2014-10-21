from nupic.data.pattern_machine import ConsecutivePatternMachine

from params import MOTOR_ENABLED_BIT_COUNT

MOTOR_PATTERN_BITS = 25

motor_pattern_machine = ConsecutivePatternMachine(MOTOR_PATTERN_BITS * MOTOR_ENABLED_BIT_COUNT, MOTOR_ENABLED_BIT_COUNT)

# Input:
# [1, 2]
#
# Assume a 3x3 grid. That means all motor commands can be summed up with a 5x5 matrix,
# assuming that there's at least one pixel on the screen and then it's illegal to push
# it out of view. Tenuous, yes.
# So basically create a two digit number, base 5. That's the bit that's flipped.
# [1, 2] => [3, 4] => 3*5 + 4
# => 19
#
# Expand this into multiple bits, as instructed (MOTOR_ENABLED_BIT_COUNT)
def action_to_motor_pattern(offset):
    assert type(offset) is list and len(offset) == 2, "Should provide an [x,y] array."

    dx = offset[0]
    dy = offset[1]

    assert abs(dx) <= 2 and abs(dy) <= 2, "Uhh, so, I assumed that you'd never move more than two squares."

    the_bit = (dx + 2)*5 + (dy + 2)

    assert the_bit < MOTOR_PATTERN_BITS, "That bit doesn't exist?"

    return motor_pattern_machine.get(the_bit)


def extract_motor_pattern(motor_value):
    return action_to_motor_pattern(motor_value)