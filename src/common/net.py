import logging
import socket
import json
from socket import timeout as TimeoutException
import struct
import time

from common import constants as c

logger = logging.getLogger()


class Net:
    def __init__(self, ip, port):
        self.ip = ip
        self.port = port
        self.connect(ip, port)

    def connect(self, ip, port):
        # logger.info('Connecting to %s:%i', ip, port)
        server_address = (ip, port)

        # todo: not sure whether to set blocking or not
        while True:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.setblocking(1)
                self.sock.connect(server_address)
                logger.log(msg='Conneted to server at: {}'.format(server_address),
                           level=15)
                break
            except ConnectionError as e:
                logger.error("Could not connect to {}:{}".format(ip, port))
                logger.error(e)
                time.sleep(1)
                # raise e

    def send(self, obj=None, message_type=''):
        if not obj:
            obj = dict()
        try:
            obj.update({c.TYPE_STR: message_type})
            json_obj = json.dumps(eval(str(obj)), ensure_ascii=False).encode(
                'utf-8')
            objlen = json_obj.__len__()
            self.sock.send(objlen.to_bytes(4, byteorder='big'))
            bytes_sent = self.sock.send(json_obj)
            while bytes_sent < objlen:
                print('Data not sent completely: ' + str(bytes_sent) + ' < ' +
                      str(json_obj.__len__()))
                bytes_sent = self.sock.send(json_obj)
            logger.debug('obj sent: ' + str(obj))
        except NameError as err:
            logger.exception('NameError in send: {}'.format(err))
            raise err
        except BrokenPipeError as err:
            logger.exception('BrokenPipeError in send: {}'.format(err))
            self.connect(self.ip, self.port)
        except ConnectionResetError as err:
            logger.exception('ConnectionResetError in send: {}'.format(err))
            self.connect(self.ip, self.port)

    def receive_message(self, msg_type, retry_type=None):
        while True:
            try:
                rec_dict = self.receive(0.5)
                if rec_dict[c.TYPE_STR] == msg_type:
                    break
                else:
                    raise Exception("Expected {}, but got {} packet.".format(msg_type, rec_dict['msg_type']))
            except Exception as e:
                # logger.error('Error in receive_message: %s.', str(e))
                self.connect(self.ip, self.port)
                # logger.info("Retry msg={}, retry={}".format(msg_type, retry_type))
                self.send(message_type=retry_type)
                # return self.receive_message(msg_type, retry_type=retry_type)

        return rec_dict

    def receive(self, wait_time=0.0):
        try:
            self.sock.settimeout(wait_time)
            rec_int_bytes = []
            while len(rec_int_bytes) < 2:
                rec_int_bytes.extend(self.sock.recv(2 - len(rec_int_bytes)))
                # if rec_int_bytes[0] == 10:
                #     rec_int_bytes = rec_int_bytes[1:]
            # logger.debug(str(rec_int_bytes))
            # rec_int = int(bytearray(rec_int_bytes))
            rec_int = int.from_bytes(bytes(rec_int_bytes), byteorder='big')
            logger.debug("Received packet size: %i", rec_int)
            # if rec_int_bytes[:1] == b'\n':
            #     rec_int += 1
            rec_bytes = []
            while len(rec_bytes) < rec_int:
                rec_bytes.extend(self.sock.recv(rec_int - len(rec_bytes)))
            rec = bytearray(rec_bytes).decode("utf-8")
        except TimeoutException as err:
            # logger.error("Error: Socket timeout in receive")
            raise err
        except ValueError as err:
            if rec_int_bytes == b'\n':
                return None
            else:
                logger.exception("Exception in receive")
                raise err
        except Exception as e:
            logger.exception("Unexpected error in receive")
            raise e
        finally:
            self.sock.settimeout(0)
        logger.debug('obj rec: ' + str(rec))
        try:
            data_dict_result = json.loads(rec.strip())
            return data_dict_result
        except json.decoder.JSONDecodeError as e:
            logger.error('error in receive: %s', str(e))
            return None