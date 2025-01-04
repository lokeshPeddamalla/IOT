from __future__ import unicode_literals
from kivy.support import install_twisted_reactor

install_twisted_reactor()

from twisted.internet import reactor
from twisted.internet.protocol import Protocol, Factory, connectionDone
from kivymd.app import MDApp
from kivy.core.window import Window

Window.size = (450, 700)

class EchoThing(Protocol):
    def connectionMade(self):
        print("Connection made")

    def dataReceived(self, data):
        data = data.decode("utf-8")
        
        if "Probe" in data:
            self.probe_handler(data)
        
        elif "Invoke" in data:
            temperature = data.split(":")[1].strip()  # Ensure no extra spaces
            self.factory.app.invoke_handler(temperature)

    def probe_handler(self, data):
        try:
            with open("ac_json/ac.json", 'r') as file:
                file_content = file.read()
                file_name = file.name
                response = f"{file_name}:file:{file_content}"
                self.transport.write(response.encode('utf-8'))
        except Exception as e:
            print(f"Error reading file: {e}")

    def connectionLost(self, reason=connectionDone):
        print("Connection lost")

class EchoServerFactory(Factory):
    protocol = EchoThing

    def __init__(self, app):
        self.app = app

class AcApp(MDApp):
    def build(self):
        reactor.listenTCP(8004, EchoServerFactory(self))
        return

    def invoke_handler(self, temperature):
        print(f"Temperature set to {temperature}")
        # Update image based on the received temperature
        if temperature == "16.5":
            self.root.ids.image.source = 'ac1.png'
        elif temperature == "16":
            self.root.ids.image.source = 'ac.png'
        else:
            print(f"Unhandled temperature value: {temperature}")

if __name__ == "__main__":
    AcApp().run()
