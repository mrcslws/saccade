import numpy

from sensorimotor.general_temporal_memory import GeneralTemporalMemory
from nupic.research.monitor_mixin.temporal_memory_monitor_mixin import (
    TemporalMemoryMonitorMixin)
from nupic.research.spatial_pooler import SpatialPooler

from saccade.params import TM_PARAMS, SP_PARAMS, COLUMN_COUNT
from saccade.utility import get_indices_of_1

class MonitoredSensorimotorTemporalMemory(TemporalMemoryMonitorMixin,
                                          GeneralTemporalMemory): pass



class Brain(object):
    def __init__(self):
        self.tm = MonitoredSensorimotorTemporalMemory(**TM_PARAMS)
        self.sp = SpatialPooler(**SP_PARAMS)

    def consume_motion(self, sensor_input, motor_input, human_readable_sensor_value):
        # Rather than connecting the sensor input directly to columns, spatial pool over the input.
        # One example where this becomes necessary: when you combine different granularities of vision.
        # When a shape moves out of low-granularity vision to high-granularity vision, it needs to expect a vague mix
        # of white and black pixels, without being surprised by any particular pixel.
        sp_output = numpy.zeros((COLUMN_COUNT,), dtype="int")
        self.sp.compute(inputVector=sensor_input,
                        learn=True,
                        activeArray=sp_output)
        active_sensor_columns = set(numpy.where(sp_output > 0)[0])

        motor_pattern_no_collisions = set(map(lambda x: x + COLUMN_COUNT, motor_input))
        sensorimotor_pattern = active_sensor_columns.union(motor_pattern_no_collisions)

        self.tm.compute(active_sensor_columns,
                        activeExternalCells=sensorimotor_pattern,
                        formInternalConnections=False,
                        learn=True,
                        sequenceLabel=str(human_readable_sensor_value))
        print self.tm.mmPrettyPrintMetrics(self.tm.mmGetDefaultMetrics())

        return {"sp_output": list(get_indices_of_1(sp_output))}


        def get_predictions_for_action(self, message):
            raise Exception("Not implemented")
