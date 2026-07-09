//package world.locations;
//
//
//import world.locations.cityLocations.*;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
////TODO: these with more locations once it's working
//public class LocationFinder {
//    /**
//     * Returns a registry of each City hotspot enum.
//     */
//    private static final List<Class<? extends Enum<? extends TravelMan>>> CITY_SPOTS = Arrays.asList(
//            AlKharidLocation.class,
//            DraynorVillageLocation.class,
//            LumbridgeLocation.class,
//            PortSarimLocation.class,
//            VarrockLocation.class
//    );
////
////    private static final List<Class<? extends Enum<? extends TravelMan>>> SKILLING_SPOTS = Arrays.asList(
////            MiningLocation.class,
////            FishingLocation.class
////    );
////
////    private static final List<Class<? extends Enum<? extends TravelMan>>> CLUE_SPOTS = Arrays.asList(
////            BeginnerClueLocation.class,
////            EasyClueLocation.class
////            // etc.
////    );
//
//    public static final List<Class<? extends Enum<? extends TravelMan>>> getAll
//            = Stream.of(CITY_SPOTS)
////                      SKILLING_SPOTS,
////                      CLUE_SPOTS)
//            .flatMap(List::stream)
//            .collect(Collectors.toList());
//
//
//
//    /**
//     * Perform a case-insensitive search across all registered location enums.
//     */
//    //TODO: TEST
//    public static TravelMan find(String name) {
//        for (Class<? extends Enum<? extends TravelMan>> enumClass : getAll) {
//            for (Enum<?> constant : enumClass.getEnumConstants()) {
//                TravelMan location = (TravelMan) constant;
//                if (location.getName().equalsIgnoreCase(name)) {
//                    return location;
//                }
//            }
//        }
//        throw new IllegalArgumentException("Unknown location: " + name);
//    }
//
//    /**
//     * Perform a case-insensitive search within the passed location enum.
//     *
//     * @param name The name of the city to attempt to find.
//     * @param locationEnumValues The location enum to search.
//     * @return The location if it is found in the {@link Spot locations} class.
//     */
//    //TODO: TEST
//    public static TravelMan find(String name, TravelMan... locationEnumValues) {
//        for (TravelMan city : locationEnumValues) {
//            if (city.getName().equalsIgnoreCase(name)) {
//                return city;
//            }
//        }
//        return null;
//    }
//
//
//    /**
//     * Perform a case-insensitive search across a specific registered location.
//     */
//    public static TravelMan findIn(TravelMan spot, String name) {
//        for (Class<? extends Enum<? extends TravelMan>> enumClass : getAll) {
//            for (Enum<?> constant : enumClass.getEnumConstants()) {
//                TravelMan location = (TravelMan) constant;
//                if (location.getName().equalsIgnoreCase(name)) {
//                    return location;
//                }
//            }
//        }
//        throw new IllegalArgumentException("Unknown location: " + name);
//    }
////
////    // --- General search (all groups) ---
////    public static TravelMan find(String name) {
////        return Stream.of(CITY_SPOTS) //, SKILLING_SPOTS, CLUE_SPOTS
////                .flatMap(List::stream)
////                .flatMap(LocationFinder::enumToStream)
////                .filter(loc -> loc.getName().equalsIgnoreCase(name))
////                .findFirst()
////                .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + name));
////    }
//
//    // Category-specific search (only cities)
//    public static TravelMan findCity(String name) {
//        return Stream.of(
//                        Arrays.stream(AlKharidLocation.values()),
//                        Arrays.stream(DraynorVillageLocation.values()),
//                        Arrays.stream(LumbridgeLocation.values()),
//                        Arrays.stream(PortSarimLocation.values()),
//                        Arrays.stream(VarrockLocation.values())
//                )
//                .flatMap(s -> s)
//                .filter(loc -> loc.getName().equalsIgnoreCase(name))
//                .findFirst()
//                .orElseThrow(() -> new IllegalArgumentException("Unknown city: " + name));
//    }
//}
//
//
