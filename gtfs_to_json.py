"""
Converts the official Israeli Ministry of Transport GTFS feed into the compact
stations.json format that the app reads (see MainActivity.java).

HOW TO GET THE SOURCE DATA (needs a regular internet connection - do this on
any normal computer, not on the Fig device):
    1. Download: https://gtfs.mot.gov.il/gtfsfiles/israel-public-transportation.zip
       (updated every night by the Ministry of Transport)
    2. Unzip it into a folder, e.g. ./gtfs_raw/
       You should see files: stops.txt, routes.txt, trips.txt, stop_times.txt

USAGE:
    python3 gtfs_to_json.py ./gtfs_raw ./stations.json

NOTE ON SIZE: the full national feed covers every operator in the country and
stop_times.txt alone can be very large (millions of rows). This script keeps
only the first departure time per line/direction as a simple example - for a
device with very limited storage you will likely want to filter to specific
operators/regions (e.g. only lines whose stops match a list of station names
you care about) rather than loading all of Israel at once. Ask whoever is
loading the file onto the device how much storage the app is allowed to use,
and adjust the FILTER section below accordingly.
"""

import csv
import json
import sys
import os
from collections import defaultdict, OrderedDict


def load_csv(path):
    with open(path, encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def main(gtfs_dir, out_path):
    stops = load_csv(os.path.join(gtfs_dir, "stops.txt"))
    routes = load_csv(os.path.join(gtfs_dir, "routes.txt"))
    trips = load_csv(os.path.join(gtfs_dir, "trips.txt"))
    stop_times = load_csv(os.path.join(gtfs_dir, "stop_times.txt"))

    stop_name_by_id = {s["stop_id"]: s["stop_name"] for s in stops}
    route_by_id = {r["route_id"]: r for r in routes}
    trip_to_route = {t["trip_id"]: t["route_id"] for t in trips}

    # Group stop_times by trip, in stop_sequence order
    times_by_trip = defaultdict(list)
    for row in stop_times:
        times_by_trip[row["trip_id"]].append(row)
    for trip_id in times_by_trip:
        times_by_trip[trip_id].sort(key=lambda r: int(r["stop_sequence"]))

    # --- FILTER SECTION -------------------------------------------------
    # By default this keeps EVERY line in the country, which will likely be
    # too big for the device. Uncomment and edit this to narrow it down,
    # e.g. only keep lines that touch a station whose name contains a
    # particular city/region:
    #
    # ALLOWED_SUBSTRINGS = ["ירושלים", "בית שמש", "מודיעין"]
    # def line_is_relevant(stop_names):
    #     return any(any(sub in name for sub in ALLOWED_SUBSTRINGS) for name in stop_names)
    # ---------------------------------------------------------------------

    seen_routes = OrderedDict()
    all_station_names = set()

    for trip_id, rows in times_by_trip.items():
        route_id = trip_to_route.get(trip_id)
        if not route_id or route_id in seen_routes:
            continue
        stop_sequence_names = [stop_name_by_id.get(r["stop_id"], "") for r in rows]
        if not all(stop_sequence_names):
            continue

        # if line_is_relevant is defined above, apply it here:
        # if not line_is_relevant(stop_sequence_names):
        #     continue

        route = route_by_id.get(route_id, {})
        seen_routes[route_id] = {
            "line": route.get("route_short_name", route_id),
            "operator": route.get("agency_id", ""),
            "stops": stop_sequence_names,
            "times": [rows[0]["departure_time"]],  # first stop departure only, per trip
        }
        all_station_names.update(stop_sequence_names)

    result = {
        "stations": sorted(all_station_names),
        "lines": list(seen_routes.values()),
    }

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)

    print(f"Wrote {len(result['lines'])} lines and {len(result['stations'])} stations to {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 gtfs_to_json.py <gtfs_folder> <output_stations.json>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
