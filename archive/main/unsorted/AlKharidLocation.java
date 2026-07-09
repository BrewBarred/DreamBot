//package main.unsorted;
//
//import locations.TravelMan;
//import org.dreambot.api.methods.map.Area;
//
//public enum AlKharidLocation implements TravelMan {
//    SHOP_PLATESKIRT(new Area(3205, 3225, 3220, 3205), "Ranaels Super Skirts", "Al'kharid plateskirt store, common clue location.");
//
//    public final Area area;
//    public final String name;
//    public final String description;
//
//    AlKharidLocation(Area area, String name) {
//        this.area = area;
//        this.name = name;
//        this.description = null;
//    }
//
//    AlKharidLocation(Area area, String name, String description) {
//        this.area = area;
//        this.name = name;
//        this.description = description;
//    }
//
//    @Override
//    public Area getArea() {
//        return area;
//    }
//
//    @Override
//    public String getName() {
//        return name;
//    }
//
//    @Override
//    public String getDescription() {
//        return description;
//    }
//
//}