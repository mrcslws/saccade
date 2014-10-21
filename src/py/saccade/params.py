from sensor import SENSOR_PATTERN_BITS

COLUMN_COUNT = 128

ACTIVE_COLUMN_TARGET = 16
MOTOR_ENABLED_BIT_COUNT = 8
DISTAL_ACTIVATION_THRESHOLD = 20

SP_PARAMS = {
    "inputDimensions": (SENSOR_PATTERN_BITS,),
    "columnDimensions": (COLUMN_COUNT,),
    "potentialRadius": 18,
    "numActiveColumnsPerInhArea": ACTIVE_COLUMN_TARGET,
    "globalInhibition": True,
    "synPermActiveInc": 0.03,
    "potentialPct": 1.0
}

TM_PARAMS = {
    "columnDimensions": [COLUMN_COUNT],
    "cellsPerColumn": 8,
    "initialPermanence": 0.5,
    "connectedPermanence": 0.6,
    "minThreshold": DISTAL_ACTIVATION_THRESHOLD,
    "activationThreshold": DISTAL_ACTIVATION_THRESHOLD,
    "maxNewSynapseCount": 50,
    "permanenceIncrement": 0.1,
    "permanenceDecrement": 0.02
}
