package jp.pirijuggler.paper.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineDataSimulationOptionsTest {
    private static MachineDataSimulationService.SimulationOptions parse(String... args){
        return MachineDataSimulationService.parseOptions(args);
    }

    @Test void defaultArgumentsKeepDefaultSkillAndNoForce(){
        assertEquals(new MachineDataSimulationService.SimulationOptions(80,false),parse("sim","10","100"));
    }

    @Test void oldFourthNumericArgumentStillMeansSkillPercent(){
        assertEquals(new MachineDataSimulationService.SimulationOptions(95,false),parse("sim","10","100","95"));
        assertEquals(new MachineDataSimulationService.SimulationOptions(0,false),parse("sim","10","100","0"));
        assertEquals(new MachineDataSimulationService.SimulationOptions(100,false),parse("sim","10","100","100"));
    }

    @Test void existingForceShorthandRemainsSupported(){
        assertEquals(new MachineDataSimulationService.SimulationOptions(80,true),parse("sim","10","100","force"));
        assertEquals(new MachineDataSimulationService.SimulationOptions(80,true),parse("sim","10","100","FORCE"));
    }

    @Test void explicitSkillPercentAndForceAreIndependent(){
        assertEquals(new MachineDataSimulationService.SimulationOptions(95,true),parse("sim","10","100","95","force"));
        assertEquals(new MachineDataSimulationService.SimulationOptions(0,true),parse("sim","10","100","0","FORCE"));
        assertEquals(new MachineDataSimulationService.SimulationOptions(100,true),parse("sim","10","100","100","force"));
    }

    @Test void invalidSkillOrForceIsRejectedRatherThanSilentlyIgnored(){
        assertThrows(IllegalArgumentException.class,()->parse("sim","10","100","101","force"));
        assertThrows(IllegalArgumentException.class,()->parse("sim","10","100","-1","force"));
        assertThrows(IllegalArgumentException.class,()->parse("sim","10","100","95","wrong"));
        assertThrows(IllegalArgumentException.class,()->parse("sim","10","100","force","95"));
        assertThrows(IllegalArgumentException.class,()->parse("sim","10","100","95","force","extra"));
        assertThrows(IllegalArgumentException.class,()->parse("sim","10"));
    }
}
