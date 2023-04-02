# COMP3100 Assignment
Currently up to stage 1 is implemented, this means a basic LRR scheduler that is functional and can bring a simulation from beginning to end gracefully assuming no errors. I have confirmed that it passes week 6 tests.

## How to use
Building: (results will be stored in `build`, do not use multiple threads)
```
$ make
```

Installing:
```
$ make PREFIX=[install path] install
```

Running: (all class files must stay together)
```
$ cd [build or install path]
$ java SimClient
```

## Testing
To use with uni-provided tests, install to the same directory as the test is in, then point the test towards `SimClient.class`, for example:
```
$ ./S1Tests-wk6.sh -n SimClient.class
```
