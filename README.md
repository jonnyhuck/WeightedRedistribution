# WeightedRedistribution

GeoTools-based Java program for performing 'Weighted Redistribution', as described in the open access academic paper [Visualizing patterns in spatially ambiguous point data](http://www.josis.org/index.php/josis/article/view/211 "JoSIS").

Called from the command line:
`java -jar WFR.jar n f [weighting surface path].tif [output path].tif`

where n (+ve integer) is the number of iterations (weightedness)
and f (between 0 and 1) is the confidence in the dataset

Once you run the program it will ask you to browse to the point and polygon datasets

It works fine, but there is a lot of work to do in terms of user-friendliness, so watch this space!
