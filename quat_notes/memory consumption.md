Quick not-very-scientific memory profiling, done by attaching VisualVM to the gradle daemon lol

Mappings memory before adding `StringInterner`:

* `MappingsWrapper#1`: **8,878,184**b retained size
* `Srg#1`: 6,522,600b retained
* `Members#1`: 1,065,696b retained
* `Members#2`: 1,044,736b retained
* `Packages#1`: 244,960b retained

Mappings memory after adding `StringInterner`:

* `MappingsWrapper#1`: **4,583,420**b retained size
* `Srg#1`: 2,298,672b retained
* `Members#1`: 810,272b retained
* `Members#2`: 734,968b retained
* `Packages#1`: 244,912b retained

-> 49% smaller 🙂 And very little performance impact

The `TinyTree` is still 6,333,584b which is not very tiny if you think about it!! but creating that is the job of `TinyMappingFactory` which is from a dependency. Idk maybe i should update it lol

Size after writing `TreeSquisher`, which reflectively interns all the strings in a `TinyTree`: 4,173,424b (34% smaller), hm, not really worth it imo (it is quite slow) 