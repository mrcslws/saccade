import json
from uuid import UUID, uuid1

from saccade.http_server_with_post_callbacks import become_a_server
from saccade.sensor import extract_sensor_pattern
from saccade.motor import extract_motor_pattern
from brain import Brain

def main():
    # Example:
    # { '20629d5753f811e49412c82a1425e54f': {"current_sensor_value": [[0, 0, 1], [0, 1, 0], [0, 0, 0]
    #                                        "matrix_rows": 3
    #                                        "matrix_columns": 3}
    # }
    #
    # Context is stored separately from the Brain because it implements a feature that is
    # not NuPICky: it lets you specify sensor values now and motor values later.
    contexts = {}

    brain = Brain()

    # Commence closure!

    # Expected message format:
    # [[0, 0, 0], [0, 0, 1], [0, 1, 0]]
    def set_initial_sensor_value(message):
        assert type(message) is list, "Please specify a sensor value (in the form of an array)"
        assert type(message[0]) is list, "...and it needs to be a matrix"

        context_id = uuid1().hex
        current_sensor_value = message
        matrix_rows = len(current_sensor_value)
        matrix_columns = len(current_sensor_value[0])

        print "New context: %s" % context_id
        print "[%s] Sensor value: %s" % (context_id, current_sensor_value)

        contexts[context_id] = {"current_sensor_value": message,
                                "matrix_rows": matrix_rows,
                                "matrix_columns": matrix_columns}

        return json.dumps(context_id)

    # Expected message format:
    # {"context_id": "20629d5753f811e49412c82a1425e54f",
    #  "motor_value": [1, 1],
    #  "new_sensor_value": [[0, 0, 0], [0, 0, 1], [0, 1, 0]]}
    def add_action_and_result(message):
        assert type(message) is dict, "Message should contain an associative array"

        context_id = message["context_id"]
        motor_value = message["motor_value"]
        new_sensor_value = message["new_sensor_value"]

        assert context_id in contexts, "Unrecognized context"

        current_sensor_value = contexts[context_id]["current_sensor_value"]

        current_sensor_pattern = extract_sensor_pattern(current_sensor_value)
        motor_pattern = extract_motor_pattern(motor_value)

        print "[%s] Motor value: %s" % (context_id, motor_value)

        response = brain.consume_motion(current_sensor_pattern, motor_pattern, current_sensor_value)
        response["sensor_value"] = current_sensor_value

        print "[%s] Sensor value: %s" % (context_id, new_sensor_value)
        contexts[context_id]["current_sensor_value"] = new_sensor_value

        return json.dumps(response)

    # Expected message format:
    # {"context_id": "20629d5753f811e49412c82a1425e54f",
    #  "motor_value": [1, 1]}
    def get_predictions_for_action(message):
        context_id = message["context_id"]
        motor_value = message["motor_value"]
        motor_pattern = extract_motor_pattern(motor_value)
        # TODO

    def get_metrics(message):
        pass

    become_a_server({"/set-initial-sensor-value": set_initial_sensor_value,
                     "/add-action-and-result": add_action_and_result,
                     "/get-predictions-for-action": get_predictions_for_action,
                     "/get-metrics": get_metrics})

if __name__ == '__main__':
    main()