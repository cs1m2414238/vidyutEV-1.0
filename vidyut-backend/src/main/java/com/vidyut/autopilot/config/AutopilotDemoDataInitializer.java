package com.vidyut.autopilot.config;

import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ChargingConnector;
import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vidyut.demo-data.enabled", havingValue = "true")
public class AutopilotDemoDataInitializer implements ApplicationRunner {

    private final ChargingStationRepository stationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Set<String> existingNames = stationRepository.findAll().stream()
                .map(ChargingStation::getName)
                .collect(java.util.stream.Collectors.toSet());

        demoStations().stream()
                .filter(station -> !existingNames.contains(station.name()))
                .map(this::toEntity)
                .forEach(stationRepository::save);
    }

    private ChargingStation toEntity(DemoStation demo) {
        ChargingStation station = ChargingStation.builder()
                .name(demo.name())
                .address(demo.address())
                .city(demo.city())
                .latitude(demo.latitude())
                .longitude(demo.longitude())
                .pricePerKwh(demo.pricePerKwh())
                .rating(demo.rating())
                .reviewCount(120)
                .amenities("Cafe, Washroom, Wi-Fi, 24x7 Security")
                .workingHours("Open 24 hours")
                .queueCount(demo.queueCount())
                .occupancyPercent(demo.occupancyPercent())
                .status(StationStatus.ACTIVE)
                .availability(StationAvailability.AVAILABLE)
                .bookingSlotMinutes(30)
                .connectors(new ArrayList<>())
                .build();
        addConnector(station, ConnectorType.CCS2, demo.powerKw(), demo.code() + "-C");
        addConnector(station, ConnectorType.TYPE2, Math.min(22, demo.powerKw()), demo.code() + "-T");
        return station;
    }

    private void addConnector(ChargingStation station, ConnectorType type, double powerKw, String code) {
        station.getConnectors().add(ChargingConnector.builder()
                .station(station)
                .type(type)
                .powerKw(powerKw)
                .available(true)
                .chargerCode(code)
                .status(ChargerStatus.ONLINE)
                .healthScore(96)
                .firmwareVersion("3.5.0")
                .build());
    }

    private List<DemoStation> demoStations() {
        return List.of(
                new DemoStation("Vidyut Kanpur Gateway", "NH-19, Kanpur Dehat", "Kanpur", 26.5220, 80.0350, 13.0, 4.8, 1, 38, 120, "VY-KNP"),
                new DemoStation("Vidyut Etawah Express", "Agra-Lucknow Expressway, Etawah", "Etawah", 26.7829, 79.0277, 12.5, 4.7, 0, 25, 150, "VY-ETW"),
                new DemoStation("GreenCharge Agra", "Fatehabad Road, Agra", "Agra", 27.1767, 78.0081, 14.2, 4.9, 3, 72, 150, "VY-AGR"),
                new DemoStation("VoltPoint Mathura", "Yamuna Expressway, Mathura", "Mathura", 27.4924, 77.6737, 13.4, 4.6, 0, 31, 180, "VY-MTR"),
                new DemoStation("Yamuna Energy Plaza", "Jewar Toll Plaza, Gautam Budh Nagar", "Greater Noida", 28.1580, 77.5540, 12.9, 4.8, 1, 42, 180, "VY-JWR"),
                new DemoStation("Vidyut Greater Noida", "Knowledge Park II, Greater Noida", "Greater Noida", 28.4744, 77.5040, 15.0, 4.9, 0, 28, 240, "VY-GNO"),
                new DemoStation("Delhi Arrival Hub", "Sarai Kale Khan, New Delhi", "Delhi", 28.5890, 77.2500, 16.2, 4.7, 2, 61, 150, "VY-DEL"),
                new DemoStation("Vidyut Jaipur NH48", "Ajmer Road, Jaipur", "Jaipur", 26.8870, 75.7050, 13.8, 4.8, 1, 36, 180, "VY-JAI"),
                new DemoStation("Vidyut Kishangarh Corridor", "NH48, Kishangarh", "Kishangarh", 26.5906, 74.8564, 12.9, 4.7, 0, 24, 180, "VY-KSG"),
                new DemoStation("Vidyut Udaipur Gateway", "NH48, Udaipur", "Udaipur", 24.6500, 73.7100, 14.0, 4.9, 1, 40, 240, "VY-UDR"),
                new DemoStation("Vidyut Ahmedabad Ring", "SG Highway, Ahmedabad", "Ahmedabad", 23.0700, 72.5000, 13.5, 4.8, 2, 52, 240, "VY-AMD"),
                new DemoStation("Vidyut Vadodara Express", "NE1 Junction, Vadodara", "Vadodara", 22.3072, 73.1812, 12.7, 4.6, 0, 30, 180, "VY-BDQ"),
                new DemoStation("Vidyut Surat NH48", "Kadodara, Surat", "Surat", 21.2200, 72.9600, 13.2, 4.8, 1, 45, 240, "VY-STV"),
                new DemoStation("Vidyut Mumbai Arrival", "Thane, Mumbai Metropolitan Region", "Mumbai", 19.2183, 72.9781, 16.0, 4.9, 2, 60, 240, "VY-BOM"),

                // Himalayan and north India spine: Leh -> Delhi.
                new DemoStation("Vidyut Leh Summit", "NH-1, Nimoo, Leh", "Leh", 34.1970, 77.4160, 15.8, 4.8, 0, 22, 120, "VY-LEH"),
                new DemoStation("Vidyut Keylong Mountain Link", "Leh-Manali Highway, Keylong", "Keylong", 32.6500, 76.9700, 13.8, 4.7, 0, 20, 120, "VY-KYL"),
                new DemoStation("Vidyut Manali Valley", "NH-3, Patlikuhal", "Manali", 32.3100, 77.1200, 13.2, 4.8, 0, 24, 150, "VY-MNL"),
                new DemoStation("Vidyut Kargil Transit", "Srinagar-Leh Highway, Kargil", "Kargil", 34.5120, 76.0800, 12.1, 4.6, 0, 18, 90, "VY-KGL"),
                new DemoStation("Vidyut Srinagar Ring", "NH-44, Pampore", "Srinagar", 34.0200, 74.9300, 13.6, 4.8, 1, 35, 180, "VY-SXR"),
                new DemoStation("Vidyut Jammu Bypass", "NH-44, Bari Brahmana", "Jammu", 32.6400, 74.9100, 12.4, 4.7, 0, 28, 180, "VY-JMU"),
                new DemoStation("Vidyut Pathankot Junction", "NH-44, Pathankot Bypass", "Pathankot", 32.2000, 75.7100, 11.3, 4.5, 1, 32, 120, "VY-PTK"),
                new DemoStation("Punjab ValueCharge Jalandhar", "GT Road, Jalandhar Cantt", "Jalandhar", 31.2500, 75.6600, 8.9, 4.4, 0, 20, 60, "VY-JAL-V"),
                new DemoStation("Vidyut Jalandhar Rapid", "NH-44, Phagwara Junction", "Jalandhar", 31.2200, 75.7500, 15.9, 4.9, 2, 62, 240, "VY-JAL-R"),
                new DemoStation("Vidyut Ludhiana Express", "GT Road, Doraha", "Ludhiana", 30.8000, 76.0200, 13.2, 4.7, 1, 36, 180, "VY-LDH"),
                new DemoStation("Vidyut Chandigarh Gateway", "Zirakpur-Ambala Road", "Chandigarh", 30.6500, 76.8200, 14.0, 4.8, 1, 45, 180, "VY-IXC"),
                new DemoStation("Vidyut Ambala NH44", "NH-44, Ambala Cantt", "Ambala", 30.2800, 76.8200, 10.8, 4.6, 0, 24, 120, "VY-AMB"),
                new DemoStation("Haryana Saver Karnal", "Karnal Bypass, NH-44", "Karnal", 29.7700, 77.0200, 8.6, 4.3, 0, 16, 60, "VY-KNL-V"),
                new DemoStation("Vidyut Karnal HyperCharge", "Karnal Lake, NH-44", "Karnal", 29.7600, 76.9100, 17.2, 4.9, 3, 76, 350, "VY-KNL-R"),
                new DemoStation("Vidyut Panipat Corridor", "NH-44, Samalkha", "Panipat", 29.2500, 77.0000, 12.0, 4.7, 1, 38, 180, "VY-PNP"),
                new DemoStation("Delhi Economy Ring", "Kundli, Delhi NCR", "Delhi", 28.7900, 77.1300, 9.2, 4.4, 0, 22, 60, "VY-DEL-V"),
                new DemoStation("Vidyut Dehradun Valley", "Haridwar Road, Doiwala", "Dehradun", 30.1900, 78.1700, 12.3, 4.7, 0, 30, 120, "VY-DED"),
                new DemoStation("Vidyut Haridwar Rest Hub", "NH-34, Bahadrabad", "Haridwar", 29.9300, 78.0600, 11.8, 4.8, 1, 32, 150, "VY-HDW"),
                new DemoStation("Vidyut Shimla Hills", "NH-5, Shoghi", "Shimla", 31.0400, 77.1200, 14.5, 4.7, 0, 25, 90, "VY-SML"),

                // Rajasthan, Kutch and the western coast.
                new DemoStation("Vidyut Rewari NH48", "Bawal Industrial Area, NH-48", "Rewari", 28.0800, 76.5900, 10.6, 4.5, 0, 20, 120, "VY-REW"),
                new DemoStation("Jaipur Budget Charge", "Shahpura, Jaipur Highway", "Jaipur", 27.0600, 75.9700, 8.7, 4.4, 0, 18, 60, "VY-JAI-V"),
                new DemoStation("Vidyut Ajmer Rest Plaza", "NH-48, Beawar Road", "Ajmer", 26.3300, 74.5900, 11.4, 4.7, 0, 28, 150, "VY-AJM"),
                new DemoStation("Vidyut Jodhpur Desert Gate", "NH-62, Dangiyawas", "Jodhpur", 26.1300, 73.1400, 12.8, 4.8, 1, 40, 180, "VY-JDH"),
                new DemoStation("Vidyut Jaisalmer Frontier", "Jaisalmer-Jodhpur Road", "Jaisalmer", 26.8500, 71.0400, 14.2, 4.7, 0, 26, 120, "VY-JSA"),
                new DemoStation("Vidyut Kota Express", "NH-52, Kota Bypass", "Kota", 25.0800, 75.9100, 12.2, 4.7, 1, 36, 180, "VY-KOT"),
                new DemoStation("Vidyut Rajkot Ring", "Ahmedabad-Rajkot Highway", "Rajkot", 22.4200, 70.9600, 12.1, 4.6, 1, 34, 180, "VY-RAJ"),
                new DemoStation("Vidyut Gandhidham Port Link", "NH-41, Gandhidham", "Gandhidham", 23.0200, 70.2500, 11.0, 4.6, 0, 25, 150, "VY-GIM"),
                new DemoStation("Vidyut Bhuj Frontier", "Bhuj-Bhachau Highway", "Bhuj", 23.1800, 69.7900, 13.5, 4.7, 0, 22, 120, "VY-BHJ"),
                new DemoStation("Vidyut Nashik NH3", "Mumbai-Agra Highway, Igatpuri", "Nashik", 19.8500, 73.5800, 11.9, 4.7, 1, 32, 180, "VY-NSK"),
                new DemoStation("Mumbai ValueCharge", "Navi Mumbai, Sion-Panvel Highway", "Mumbai", 19.0400, 73.1000, 9.8, 4.5, 0, 26, 90, "VY-BOM-V"),
                new DemoStation("Vidyut Pune Express", "Mumbai-Pune Expressway, Talegaon", "Pune", 18.7300, 73.6800, 14.6, 4.9, 2, 58, 240, "VY-PNQ"),
                new DemoStation("Pune Economy Plug", "NH-48, Khed Shivapur", "Pune", 18.3300, 73.8500, 8.8, 4.3, 0, 15, 60, "VY-PNQ-V"),
                new DemoStation("Vidyut Kolhapur Rest Hub", "NH-48, Kini Toll", "Kolhapur", 16.8200, 74.2200, 11.2, 4.7, 1, 30, 150, "VY-KOP"),
                new DemoStation("Vidyut Goa Coastal", "NH-66, Verna", "Goa", 15.3500, 73.9300, 13.6, 4.8, 1, 42, 180, "VY-GOI"),

                // Central India cross-country mesh.
                new DemoStation("Vidyut Gwalior NH44", "Gwalior Bypass, NH-44", "Gwalior", 26.1000, 78.2400, 11.5, 4.6, 0, 24, 150, "VY-GWL"),
                new DemoStation("Vidyut Jhansi Link", "NH-44, Babina Road", "Jhansi", 25.3300, 78.5500, 10.7, 4.6, 0, 22, 120, "VY-JHS"),
                new DemoStation("Vidyut Sagar Central", "NH-44, Sagar Bypass", "Sagar", 23.7300, 78.8100, 11.1, 4.7, 1, 30, 150, "VY-SAG"),
                new DemoStation("Bhopal ValueCharge", "Bhopal Bypass, Sehore Road", "Bhopal", 23.1600, 77.2900, 8.5, 4.3, 0, 18, 60, "VY-BHO-V"),
                new DemoStation("Vidyut Bhopal Rapid", "Hoshangabad Road, Bhopal", "Bhopal", 23.1200, 77.5100, 16.4, 4.9, 2, 66, 300, "VY-BHO-R"),
                new DemoStation("Vidyut Indore Bypass", "AB Road, Rau", "Indore", 22.6400, 75.7400, 12.0, 4.7, 1, 35, 180, "VY-IDR"),
                new DemoStation("Vidyut Dhule Junction", "NH-52, Dhule Bypass", "Dhule", 20.9900, 74.8900, 10.5, 4.5, 0, 20, 120, "VY-DHL"),
                new DemoStation("Vidyut Aurangabad Link", "Samruddhi Expressway Interchange", "Aurangabad", 19.9700, 75.4600, 12.4, 4.7, 1, 38, 180, "VY-IXU"),
                new DemoStation("Vidyut Amravati Express", "Samruddhi Expressway, Amravati", "Amravati", 20.8500, 77.9000, 11.7, 4.7, 0, 27, 180, "VY-AMI"),
                new DemoStation("Nagpur ValueCharge", "Wardha Road, Butibori", "Nagpur", 20.9300, 79.0000, 9.1, 4.4, 0, 17, 90, "VY-NAG-V"),
                new DemoStation("Vidyut Nagpur HyperCharge", "Samruddhi Zero Mile Hub", "Nagpur", 21.2600, 79.1500, 17.5, 4.9, 3, 78, 350, "VY-NAG-R"),
                new DemoStation("Vidyut Jabalpur Central", "NH-30, Jabalpur Bypass", "Jabalpur", 23.0800, 80.0900, 11.6, 4.7, 0, 28, 150, "VY-JBP"),
                new DemoStation("Vidyut Raipur Ring", "NH-53, Raipur Bypass", "Raipur", 21.1800, 81.7600, 12.0, 4.8, 1, 36, 180, "VY-RPR"),
                new DemoStation("Vidyut Sambalpur Link", "NH-53, Sambalpur", "Sambalpur", 21.3900, 84.1000, 10.9, 4.6, 0, 25, 150, "VY-SBP"),

                // Gangetic, eastern and north-eastern corridors.
                new DemoStation("Vidyut Lucknow Outer Ring", "Agra-Lucknow Expressway, Lucknow", "Lucknow", 26.9200, 80.7800, 12.0, 4.8, 1, 34, 180, "VY-LKO"),
                new DemoStation("Vidyut Prayagraj NH19", "Handia, Prayagraj", "Prayagraj", 25.3800, 82.0100, 10.8, 4.6, 0, 23, 150, "VY-PRY"),
                new DemoStation("Vidyut Varanasi Ring", "NH-19, Mohansarai", "Varanasi", 25.2500, 82.8500, 12.2, 4.8, 1, 40, 180, "VY-VNS"),
                new DemoStation("Vidyut Gorakhpur Link", "NH-27, Gorakhpur Bypass", "Gorakhpur", 26.6800, 83.5200, 11.0, 4.6, 0, 24, 120, "VY-GOP"),
                new DemoStation("Vidyut Patna Gateway", "Bihta, Patna", "Patna", 25.5600, 84.8800, 12.1, 4.7, 1, 38, 180, "VY-PAT"),
                new DemoStation("Vidyut Ranchi Ring", "NH-33, Ormanjhi", "Ranchi", 23.4800, 85.4400, 11.5, 4.7, 0, 26, 150, "VY-IXR"),
                new DemoStation("Vidyut Dhanbad NH19", "Govindpur, Dhanbad", "Dhanbad", 23.8300, 86.5200, 10.6, 4.6, 0, 22, 120, "VY-DBD"),
                new DemoStation("Vidyut Durgapur Express", "NH-19, Durgapur", "Durgapur", 23.4700, 87.4300, 11.8, 4.7, 1, 32, 180, "VY-RDP"),
                new DemoStation("Kolkata ValueCharge", "Dankuni, Kolkata Metropolitan Area", "Kolkata", 22.6700, 88.3000, 8.9, 4.4, 0, 20, 90, "VY-CCU-V"),
                new DemoStation("Vidyut Kolkata HyperCharge", "New Town, Kolkata", "Kolkata", 22.6200, 88.5200, 17.0, 4.9, 3, 74, 350, "VY-CCU-R"),
                new DemoStation("Vidyut Berhampore Link", "NH-12, Berhampore", "Berhampore", 24.0000, 88.3200, 10.2, 4.5, 0, 20, 120, "VY-BPC"),
                new DemoStation("Vidyut Malda NH12", "Malda Bypass", "Malda", 24.9000, 88.0900, 11.1, 4.6, 0, 24, 150, "VY-LDA"),
                new DemoStation("Vidyut Raiganj Link", "NH-27, Raiganj", "Raiganj", 25.7200, 88.1600, 10.4, 4.5, 0, 21, 120, "VY-RGJ"),
                new DemoStation("Vidyut Siliguri Gateway", "Asian Highway 2, Siliguri", "Siliguri", 26.6500, 88.5100, 12.6, 4.8, 1, 36, 180, "VY-IXB"),
                new DemoStation("Vidyut Alipurduar NH27", "NH-27, Alipurduar", "Alipurduar", 26.4100, 89.6200, 10.7, 4.6, 0, 24, 120, "VY-APD"),
                new DemoStation("Vidyut Kokrajhar Link", "NH-27, Kokrajhar", "Kokrajhar", 26.3400, 90.3900, 10.5, 4.6, 0, 22, 120, "VY-KOJ"),
                new DemoStation("Vidyut Guwahati Gateway", "NH-27, Amingaon", "Guwahati", 26.1900, 91.6500, 12.8, 4.8, 1, 40, 180, "VY-GAU"),
                new DemoStation("Vidyut Shillong Hills", "NH-6, Umiam", "Shillong", 25.6600, 91.9100, 13.5, 4.7, 0, 25, 120, "VY-SHL"),
                new DemoStation("Vidyut Nagaon Corridor", "NH-27, Nagaon", "Nagaon", 26.2700, 92.8100, 10.8, 4.6, 0, 24, 120, "VY-NGN"),
                new DemoStation("Vidyut Dimapur Gateway", "NH-29, Dimapur", "Dimapur", 25.8200, 93.8200, 11.9, 4.7, 0, 28, 120, "VY-DMU"),
                new DemoStation("Vidyut Kohima Hills", "NH-29, Kohima", "Kohima", 25.5900, 94.0500, 13.0, 4.7, 0, 24, 90, "VY-KOH"),
                new DemoStation("Vidyut Imphal Frontier", "NH-2, Imphal", "Imphal", 24.7300, 93.9900, 13.4, 4.8, 0, 26, 120, "VY-IMF"),
                new DemoStation("Vidyut Haflong Hills", "NH-27, Haflong", "Haflong", 25.0900, 93.1000, 12.6, 4.6, 0, 22, 90, "VY-HFG"),
                new DemoStation("Vidyut Silchar Valley", "NH-37, Silchar", "Silchar", 24.7400, 92.8500, 11.8, 4.7, 0, 25, 120, "VY-IXS"),
                new DemoStation("Vidyut Aizawl Hills", "NH-6, Aizawl", "Aizawl", 23.6500, 92.7800, 13.2, 4.7, 0, 24, 90, "VY-AJL"),
                new DemoStation("Vidyut Agartala Border Hub", "NH-8, Agartala West", "Agartala", 23.8300, 91.1400, 11.6, 4.7, 0, 25, 150, "VY-IXA"),

                // East coast and southern national corridors.
                new DemoStation("Vidyut Bhubaneswar Gateway", "NH-16, Khordha", "Bhubaneswar", 20.1700, 85.7100, 11.7, 4.8, 1, 34, 180, "VY-BBI"),
                new DemoStation("Vidyut Berhampur Coast", "NH-16, Berhampur", "Berhampur", 19.2100, 84.8600, 10.8, 4.6, 0, 22, 150, "VY-BAM"),
                new DemoStation("Vidyut Visakhapatnam Coast", "NH-16, Anandapuram", "Visakhapatnam", 17.8900, 83.3800, 12.9, 4.8, 1, 42, 240, "VY-VTZ"),
                new DemoStation("Vidyut Rajahmundry Link", "NH-16, Rajahmundry", "Rajahmundry", 16.9000, 81.9000, 11.1, 4.7, 0, 25, 150, "VY-RJA"),
                new DemoStation("Vidyut Vijayawada Express", "NH-16, Gannavaram", "Vijayawada", 16.5500, 80.8000, 12.0, 4.8, 1, 36, 180, "VY-VGA"),
                new DemoStation("Vidyut Nellore Coast", "NH-16, Nellore Bypass", "Nellore", 14.3500, 80.0500, 10.7, 4.6, 0, 22, 150, "VY-NLR"),
                new DemoStation("Chennai ValueCharge", "GST Road, Chengalpattu", "Chennai", 12.6900, 79.9800, 8.8, 4.4, 0, 19, 90, "VY-MAA-V"),
                new DemoStation("Vidyut Chennai HyperCharge", "Outer Ring Road, Chennai", "Chennai", 13.0700, 80.0700, 17.4, 4.9, 3, 76, 350, "VY-MAA-R"),
                new DemoStation("Vidyut Puducherry Coast", "ECR, Puducherry", "Puducherry", 11.8400, 79.8500, 11.4, 4.7, 0, 26, 150, "VY-PNY"),
                new DemoStation("Vidyut Tirupati Link", "NH-71, Renigunta", "Tirupati", 13.6500, 79.5200, 11.6, 4.7, 0, 25, 150, "VY-TIR"),
                new DemoStation("Vidyut Adilabad NH44", "NH-44, Adilabad", "Adilabad", 19.5600, 78.5700, 10.9, 4.6, 0, 24, 150, "VY-ADB"),
                new DemoStation("Vidyut Nizamabad Link", "NH-44, Dichpally", "Nizamabad", 18.5600, 78.1300, 10.6, 4.6, 0, 22, 150, "VY-NZB"),
                new DemoStation("Hyderabad ValueCharge", "Medchal, Hyderabad", "Hyderabad", 17.6300, 78.4900, 8.7, 4.4, 0, 18, 90, "VY-HYD-V"),
                new DemoStation("Vidyut Hyderabad HyperCharge", "Outer Ring Road, Shamshabad", "Hyderabad", 17.2400, 78.4300, 17.1, 4.9, 3, 72, 350, "VY-HYD-R"),
                new DemoStation("Vidyut Kurnool NH44", "NH-44, Kurnool Bypass", "Kurnool", 15.7100, 78.0800, 11.0, 4.7, 0, 24, 180, "VY-KJB"),
                new DemoStation("Vidyut Anantapur Link", "NH-44, Anantapur", "Anantapur", 14.5600, 77.6300, 10.8, 4.6, 0, 23, 180, "VY-ATP"),
                new DemoStation("Bengaluru ValueCharge", "Tumakuru Road, Bengaluru", "Bengaluru", 13.1200, 77.4800, 8.9, 4.5, 0, 20, 90, "VY-BLR-V"),
                new DemoStation("Vidyut Bengaluru HyperCharge", "Electronic City, Bengaluru", "Bengaluru", 12.8200, 77.6800, 17.8, 4.9, 4, 82, 350, "VY-BLR-R"),
                new DemoStation("Vidyut Mysuru Road", "Bengaluru-Mysuru Expressway", "Mysuru", 12.3900, 76.7300, 11.3, 4.7, 0, 26, 180, "VY-MYS"),
                new DemoStation("Vidyut Hubballi Junction", "NH-48, Hubballi Bypass", "Hubballi", 15.2500, 75.1200, 10.8, 4.6, 0, 24, 180, "VY-HBX"),
                new DemoStation("Vidyut Belagavi NH48", "NH-48, Belagavi", "Belagavi", 15.7300, 74.5200, 10.9, 4.6, 0, 24, 180, "VY-IXG"),
                new DemoStation("Vidyut Mangaluru Coast", "NH-66, Mulki", "Mangaluru", 13.0900, 74.8000, 12.0, 4.8, 1, 34, 180, "VY-IXE"),
                new DemoStation("Vidyut Salem Junction", "NH-44, Salem Bypass", "Salem", 11.7600, 78.2000, 10.7, 4.7, 0, 24, 180, "VY-SXV"),
                new DemoStation("Vidyut Coimbatore Gateway", "NH-544, Avinashi", "Coimbatore", 11.1800, 77.1600, 11.4, 4.8, 1, 32, 180, "VY-CJB"),
                new DemoStation("Vidyut Kochi Smart Hub", "NH-544, Aluva", "Kochi", 10.1000, 76.3500, 12.5, 4.9, 1, 40, 240, "VY-COK"),
                new DemoStation("Vidyut Madurai Ring", "NH-44, Madurai Bypass", "Madurai", 9.8300, 78.0600, 11.1, 4.7, 0, 25, 180, "VY-IXM"),
                new DemoStation("Vidyut Thiruvananthapuram", "NH-66, Kazhakkoottam", "Thiruvananthapuram", 8.6100, 76.8800, 12.1, 4.8, 1, 34, 180, "VY-TRV"),
                new DemoStation("Vidyut Kanyakumari Cape", "NH-44, Nagercoil", "Kanyakumari", 8.1800, 77.4300, 12.8, 4.8, 0, 26, 150, "VY-CAPE")
        );
    }

    private record DemoStation(
            String name,
            String address,
            String city,
            double latitude,
            double longitude,
            double pricePerKwh,
            double rating,
            int queueCount,
            double occupancyPercent,
            double powerKw,
            String code
    ) {}
}
