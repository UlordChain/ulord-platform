#-*- coding: UTF-8 -*-
import json
import logging
import os
import sys

import requests
from uwallet.commands import  config_variables, get_parser, known_commands
from uwallet.daemon import Daemon, get_daemon
from uwallet.network import Network, SimpleConfig
from uwallet.util import json_decode, prompt_password

log = logging.getLogger("uwallet")

script_dir = os.path.dirname(os.path.realpath(__file__))
is_bundle = getattr(sys, 'frozen', False)


def main():
    # make sure that certificates are here
    assert os.path.exists(requests.utils.DEFAULT_CA_BUNDLE_PATH)

    # on osx, delete Process Serial Number arg generated for apps launched in Finder
    sys.argv = filter(lambda x: not x.startswith('-psn'), sys.argv)

    # old 'help' syntax
    if len(sys.argv) > 1 and sys.argv[1] == 'help':
        sys.argv.remove('help')
        sys.argv.append('-h')

    # read arguments from stdin pipe and prompt
    for i, arg in enumerate(sys.argv):
        if arg == '-':
            if not sys.stdin.isatty():
                sys.argv[i] = sys.stdin.read()
                break
            else:
                raise BaseException('Cannot get argument from stdin')
        elif arg == '?':
            sys.argv[i] = raw_input("Enter argument:")
        elif arg == ':':
            sys.argv[i] = prompt_password('Enter argument (will not echo):', False)
    # parse command line
    parser = get_parser()
    args = parser.parse_args()

    if args.verbose:
        logging.getLogger("uwallet").setLevel(logging.INFO)
    else:
        logging.getLogger("uwallet").setLevel(logging.ERROR)
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)-8s "
                                           "%(name)s:%(lineno)d: %(message)s"))
    logging.getLogger("uwallet").addHandler(handler)

    # config is an object passed to the various constructors (wallet, interface)
    config_options = args.__dict__
    for k, v in config_options.items():
        if v is None or (k in config_variables.get(args.cmd, {}).keys()):
            config_options.pop(k)
    if config_options.get('server'):
        config_options['auto_connect'] = False

    config = SimpleConfig(config_options)
    cmdname = config.get('cmd')

    # check if a daemon is running
    server = get_daemon(config)

    # Daemon is running
    if server is not None:
        if cmdname == 'daemon':
            result = server.daemon(config_options)
        else:
            # command line
            # 因为这里的调用也需要以来daemon， 所以known_command也被赋值
            cmd = known_commands[cmdname]
            # arguments passed to function
            args = map(lambda x: config.get(x), cmd.params)
            # decode json arguments
            args = map(json_decode, args)
            # options
            args += map(lambda x: config.get(x), cmd.options)
            # 标记此次调用为命令行调用
            args.insert(0, 'is_command')
            method = 'server.{}(*{})'.format(cmdname, tuple(args))

            result = eval(method)
        print json.dumps(result, indent=2)
    # Daemon not running
    else:
        subcommand = config.get('subcommand')
        if subcommand in ['status', 'stop']:
            sys.exit("Daemon not running")
        elif subcommand == 'start':
            if hasattr(os, "fork"):
                # p = os.fork()
                p = 0
            else:
                log.warning("Cannot start uwallet daemon as a background process")
                log.warning("To use uwallet commands, run them from a different window")
                p = 0
            if p == 0:
                network = Network(config)
                network.start()
                daemon = Daemon(config, network)
                # daemon.runProc()
                # daemon.start()
                daemon.server.serve_forever()
            else:
                print "starting daemon (PID %d)" % p
        else:
            print "syntax: uwallet daemon <start|status|stop>"
        sys.exit()


if __name__ == '__main__':
    import sys

    # -------- create wallet ----------------
    # sys.argv.append('create')
    # sys.argv.append('31f7e6703d5c11e893fcf48e3889c8ab_justin')
    # sys.argv.append('123')

    # -------- create wallet ----------------
    # sys.argv.append('getbalance')
    # sys.argv.append('test_create111')
    # sys.argv.append('123')

    # -------- list address ----------------
    # sys.argv.append('listaddresses')
    # sys.argv.append('test_hetao')
    # sys.argv.append('123')

    #  -------- send to address ----------------
    # sys.argv.append('paytoandsend')x
    # sys.argv.append('uWNsSvyHPTmwd2eUxhtLPXPSBZP6neJRfz')
    # sys.argv.append('50')
    # sys.argv.append('default_wallet')

    # -------- start daemon ----------------
    sys.argv.append('daemon')
    sys.argv.append('start')
    sys.argv.append('-v')

    print sys.argv
    rs = main()

