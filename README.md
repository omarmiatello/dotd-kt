# dotd.kt
A "Dns Over TLS" deamon in written in Koltin. Inspired by https://github.com/adnsio/dotd

### Project Status
Experiment

### Performance
Tested with:

    dnsperf -d queryfile-example-10million-201202 -c 1 -T 4 -s 127.0.0.1

result:

    Statistics:
    
      Queries sent:         59065
      Queries completed:    58942 (99.79%)
      Queries lost:         23 (0.04%)
      Queries interrupted:  100 (0.17%)
    
      Response codes:       NOERROR 36944 (62.68%), SERVFAIL 712 (1.21%), NXDOMAIN 12740 (21.61%), REFUSED 8546 (14.50%)
      Average packet size:  request 38, response 88
      Run time (s):         33.632398
      Queries per second:   1752.536349
    
      Average Latency (s):  0.053591 (min 0.000106, max 4.906449)
      Latency StdDev (s):   0.244927