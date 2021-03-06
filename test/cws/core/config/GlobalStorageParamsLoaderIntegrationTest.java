package cws.core.config;

import static junit.framework.Assert.assertEquals;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.junit.Before;
import org.junit.Test;

import cws.core.exception.IllegalCWSArgumentException;
import cws.core.storage.global.GlobalStorageParams;

public class GlobalStorageParamsLoaderIntegrationTest {
    private GlobalStorageParamsLoader loader;

    private CommandLine parseArgs(String[] args) throws ParseException {
        Options options = new Options();
        GlobalStorageParamsLoader.buildCliOptions(options);
        CommandLineParser parser = new PosixParser();
        return parser.parse(options, args);
    }

    @Before
    public void setUp() throws Exception {
        loader = new GlobalStorageParamsLoader();
    }

    @Test
    public void shouldLoadDefaultVM() throws ParseException {
        CommandLine cmd = parseArgs(new String[] {});

        GlobalStorageParams globalStorageParams = loader.determineGlobalStorageParams(cmd);

        assertEquals(30000000.0, globalStorageParams.getReadSpeed());
        assertEquals(10000000.0, globalStorageParams.getWriteSpeed());
        assertEquals(0.01, globalStorageParams.getLatency());
        assertEquals(1, globalStorageParams.getNumReplicas());
        assertEquals(1.0, globalStorageParams.getChunkTransferTime());
    }

    @Test
    public void shouldLoadCustomVM() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_TYPE_OPTION_NAME,
                "../test/test.gs.yaml" });

        GlobalStorageParams globalStorageParams = loader.determineGlobalStorageParams(cmd);

        assertEquals(12345678.0, globalStorageParams.getReadSpeed());
        assertEquals(3216547.0, globalStorageParams.getWriteSpeed());
        assertEquals(0.15, globalStorageParams.getLatency());
        assertEquals(2, globalStorageParams.getNumReplicas());
        assertEquals(3.0, globalStorageParams.getChunkTransferTime());
    }

    @Test(expected = IllegalCWSArgumentException.class)
    public void shouldFailWhenFilePathIsInvalid() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_TYPE_OPTION_NAME,
                "nosuchfile.vm.yaml" });

        loader.determineGlobalStorageParams(cmd);
    }

    @Test(expected = IllegalCWSArgumentException.class)
    public void shouldFailWhenConfigIsInvalid() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_TYPE_OPTION_NAME,
                "../test/invalid.gs.yaml" });
        loader.determineGlobalStorageParams(cmd);
    }

    @Test
    public void shouldLoadVMFromCustomPath() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_CONFIGS_DIRECTORY_OPTION_NAME,
                "test", "--" + GlobalStorageParamsLoader.GS_TYPE_OPTION_NAME, "test.gs.yaml" });

        GlobalStorageParams globalStorageParams = loader.determineGlobalStorageParams(cmd);

        assertEquals(12345678.0, globalStorageParams.getReadSpeed());
    }

    @Test
    public void shouldAllowToOverrideConfigEntries() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_READ_SPEED_OPTION_NAME, "3333",
                "--" + GlobalStorageParamsLoader.GS_WRITE_SPEED_OPTION_NAME, "555.3",
                "--" + GlobalStorageParamsLoader.GS_LATENCY_OPTION_NAME, "0.23",
                "--" + GlobalStorageParamsLoader.GS_CHUNK_TRANSFER_TIME_OPTION_NAME, "2.3",
                "--" + GlobalStorageParamsLoader.GS_REPLICAS_NUMBER_OPTION_NAME, "4", });

        GlobalStorageParams globalStorageParams = loader.determineGlobalStorageParams(cmd);

        assertEquals(3333.0, globalStorageParams.getReadSpeed());
        assertEquals(555.3, globalStorageParams.getWriteSpeed());
        assertEquals(0.23, globalStorageParams.getLatency());
        assertEquals(4, globalStorageParams.getNumReplicas());
        assertEquals(2.3, globalStorageParams.getChunkTransferTime());
    }

    @Test(expected = IllegalCWSArgumentException.class)
    public void shouldFailIfOverrodeConfigIsNotValid() throws ParseException {
        CommandLine cmd = parseArgs(new String[] { "--" + GlobalStorageParamsLoader.GS_READ_SPEED_OPTION_NAME,
                "invalid" });

        loader.determineGlobalStorageParams(cmd);
    }
}
