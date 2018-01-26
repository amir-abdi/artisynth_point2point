from artisynth_helper.libraries import *

def main():
    #artisynth.core.driver.Main() #??
    Main = artisynth.core.driver.Main.getMain()
    if Main is not None:
        Main.quit()
    artisynth.core.driver.Main.setMain(None)
    artisynth.core.driver.Main.main([])
    Main = artisynth.core.driver.Main.getMain()
    jythonInit.Main = Main

    print(Main)
    loadModel('artisynth.demos.fem.HexFrame')
    play(2.5)

if __name__ == '__main__':
    main()