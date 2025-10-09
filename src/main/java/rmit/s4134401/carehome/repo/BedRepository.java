package rmit.s4134401.carehome.repo;

import java.util.List;
import java.util.Optional;

public interface BedRepository {
    Optional<Integer> findBedId(String ward, int room, int bedNum);
    List<int[]> listCoords(); 
}
