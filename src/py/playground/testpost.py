import requests

DESTINATION = "http://localhost:8000/set-initial-sensor-value"
DATA="[[0, 0, 0], [0, 1, 0], [0, 0, 1]]"

if __name__ == '__main__':
	response = requests.post(DESTINATION, data=DATA)
	print "Posted to %s: %s" % (DESTINATION, DATA)
	print "Response: %s: %s" % (response.status_code, response.text)