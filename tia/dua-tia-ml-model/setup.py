from setuptools import setup, find_packages

setup(
    name="tia-ml",  # Name of your package
    version="0.1.0",  # Initial version
    packages=find_packages(where="src"),  # Finds all packages in the "src" folder
    package_dir={"": "src"},  # Points to the src directory for package contents
    include_package_data=True,  # To include data files (like config files)
    data_files=[
        (
            "config",
            [
                "src/config/conf.yml",
                "src/config/earth-explorers-keywords.yml",
                "src/config/ensemble.yml",
                "src/config/cos-sim.yml",
            ],
        )
    ],
    install_requires=[],
)
